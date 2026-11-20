package de.bob.challenges.mobs;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

/**
 * Etappe 3: Aussehen. Jeder verwandelte Spieler bekommt einen echten Mob als „Kostüm“, der ihm
 * jeden Tick folgt (ohne KI, lautlos, ohne Schwerkraft, schiebt niemanden). Die anderen sehen den Mob,
 * der Spieler selbst wird bei ihnen ausgeblendet (Client-Mixin). Treffer auf den Mob gehen an den Spieler –
 * damit passt auch die Hitbox zum Mob. Der Spieler selbst sieht sein eigenes Kostüm nicht (sonst
 * würde es ihm die Sicht versperren).
 */
public final class Disguises {
	private static final String TEAM = "challenges_mobs";

	private final MobChallenge challenge;
	/** Spieler → Kostüm */
	private final Map<UUID, Mob> byPlayer = new HashMap<>();
	/** Kostüm → Spieler */
	private final Map<UUID, UUID> owners = new HashMap<>();
	private final Map<UUID, Integer> shownSize = new HashMap<>();
	private final Map<UUID, Long> spawnedAt = new HashMap<>();
	private long tick;

	Disguises(MobChallenge challenge) {
		this.challenge = challenge;
	}

	public boolean isDisguise(Entity entity) {
		return entity != null && owners.containsKey(entity.getUUID());
	}

	public boolean isOwnDisguise(Entity entity, ServerPlayer player) {
		return !owners.isEmpty() && player.getUUID().equals(owners.get(entity.getUUID()));
	}

	public ServerPlayer ownerOf(Entity entity, MinecraftServer server) {
		UUID owner = entity == null ? null : owners.get(entity.getUUID());
		return owner == null || server == null ? null : server.getPlayerList().getPlayer(owner);
	}

	void tick(MinecraftServer server) {
		tick++;
		Set<UUID> keep = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			MobKind kind = challenge.kindOf(player);
			if (kind == null || player.isSpectator() || !player.isAlive() || player.isRemoved()) {
				continue;
			}
			keep.add(player.getUUID());
			Mob mob = byPlayer.get(player.getUUID());
			if (mob == null || mob.isRemoved() || !mob.isAlive() || mob.level() != player.level()
					|| !BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath().equals(kind.entityId)) {
				if (mob != null) {
					remove(server, player.getUUID());
				}
				mob = spawn(server, player, kind);
				if (mob == null) {
					continue;
				}
			}
			if (kind == MobKind.MAGMA_CUBE) {
				int size = challenge.magmaSize(player);
				if (shownSize.getOrDefault(mob.getUUID(), -1) != size) {
					shownSize.put(mob.getUUID(), size);
					setSlimeSize(mob, size == 3 ? 4 : size == 2 ? 2 : 1);
				}
			}
			follow(mob, player);
			// Das eigene Kostüm beim Spieler selbst ausblenden (anfangs jeden Tick, danach ab und zu)
			long age = tick - spawnedAt.getOrDefault(mob.getUUID(), tick);
			if (age < 10 || tick % 20 == 0) {
				player.connection.send(new ClientboundRemoveEntitiesPacket(mob.getId()));
			}
		}
		for (UUID id : new HashSet<>(byPlayer.keySet())) {
			if (!keep.contains(id)) {
				remove(server, id);
			}
		}
	}

	private Mob spawn(MinecraftServer server, ServerPlayer player, MobKind kind) {
		Identifier key = Identifier.withDefaultNamespace(kind.entityId);
		if (!BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
			return null;
		}
		ServerLevel level = player.level();
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(key);
		Entity entity = type.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (!(entity instanceof Mob mob)) {
			if (entity != null) {
				entity.discard();
			}
			return null;
		}
		mob.setNoAi(true);
		mob.setSilent(true);
		mob.setNoGravity(true);
		mob.setPersistenceRequired();
		mob.setCustomName(Component.literal(player.getName().getString()));
		mob.setCustomNameVisible(true);
		if (kind == MobKind.MAGMA_CUBE) {
			int size = challenge.magmaSize(player);
			setSlimeSize(mob, size == 3 ? 4 : size == 2 ? 2 : 1);
			shownSize.put(mob.getUUID(), size);
		}
		follow(mob, player);
		owners.put(mob.getUUID(), player.getUUID());
		byPlayer.put(player.getUUID(), mob);
		spawnedAt.put(mob.getUUID(), tick);
		joinTeam(server, mob);
		level.addFreshEntity(mob);
		return mob;
	}

	private static void follow(Mob mob, ServerPlayer player) {
		mob.setDeltaMovement(Vec3.ZERO);
		mob.setPos(player.getX(), player.getY(), player.getZ());
		mob.setYRot(player.getYRot());
		mob.setXRot(player.getXRot());
		mob.setYHeadRot(player.getYHeadRot());
		mob.setYBodyRot(player.yBodyRot);
		mob.fallDistance = 0;
	}

	private static void setSlimeSize(Mob mob, int size) {
		try {
			mob.getClass().getMethod("setSize", int.class, boolean.class).invoke(mob, size, false);
		} catch (ReflectiveOperationException ignored) {
			// dann eben in Standardgröße
		}
	}

	/** Eigenes Team ohne Kollision, damit das Kostüm niemanden wegschiebt (auch nicht den Spieler selbst). */
	private static void joinTeam(MinecraftServer server, Mob mob) {
		Scoreboard scoreboard = server.getScoreboard();
		PlayerTeam team = scoreboard.getPlayerTeam(TEAM);
		if (team == null) {
			team = scoreboard.addPlayerTeam(TEAM);
		}
		team.setCollisionRule(Team.CollisionRule.NEVER);
		scoreboard.addPlayerToTeam(mob.getScoreboardName(), team);
	}

	private void remove(MinecraftServer server, UUID player) {
		Mob mob = byPlayer.remove(player);
		if (mob == null) {
			return;
		}
		owners.remove(mob.getUUID());
		shownSize.remove(mob.getUUID());
		spawnedAt.remove(mob.getUUID());
		if (server != null) {
			Scoreboard scoreboard = server.getScoreboard();
			PlayerTeam team = scoreboard.getPlayerTeam(TEAM);
			if (team != null && team.getPlayers().contains(mob.getScoreboardName())) {
				scoreboard.removePlayerFromTeam(mob.getScoreboardName(), team);
			}
		}
		mob.discard();
	}

	public void removeAll(MinecraftServer server) {
		for (UUID id : new HashSet<>(byPlayer.keySet())) {
			remove(server, id);
		}
	}

	/**
	 * Schaden am Kostüm: nichts davon trifft den Mob selbst. Schläge und Angriffe von anderen gehen an den Spieler.
	 * @return false = Schaden am Kostüm verhindern
	 */
	boolean allowDamage(Entity disguise, DamageSource source, float amount) {
		MinecraftServer server = disguise.level().getServer();
		ServerPlayer owner = ownerOf(disguise, server);
		Entity attacker = source.getEntity();
		if (owner != null && attacker != null && attacker != owner && !isDisguise(attacker)
				&& !source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
			owner.hurtServer(owner.level(), source, amount);
		}
		return false;
	}

	/** Nach einem Neustart: übrig gebliebene Kostüme ohne Spieler entfernen. */
	public void onEntityLoad(Entity entity) {
		if (entity instanceof Mob mob && !owners.containsKey(mob.getUUID()) && mob.isNoAi() && mob.isSilent()
				&& mob.isNoGravity() && mob.isCustomNameVisible() && mob.getTeam() != null
				&& TEAM.equals(mob.getTeam().getName())) {
			mob.discard();
		}
	}
}
