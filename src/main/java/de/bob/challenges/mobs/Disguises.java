package de.bob.challenges.mobs;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
 * jeden Tick folgt (ohne KI, lautlos, ohne Schwerkraft, schiebt niemanden). Der Spieler selbst wird
 * überall ausgeblendet (Client-Mixin), man sieht nur den Mob – auch sich selbst in F5. In der Ich-Perspektive
 * wird das eigene Kostüm ausgeblendet und lässt sich nicht anklicken. Treffer auf den Mob gehen an den Spieler.
 */
public final class Disguises {
	private static final String TEAM = "challenges_mobs";
	private static final UUID NOBODY = new UUID(0, 0);

	private final MobChallenge challenge;
	/** Spieler → Kostüm */
	private final Map<UUID, Mob> byPlayer = new HashMap<>();
	/** Kostüm → Spieler */
	private final Map<UUID, UUID> owners = new HashMap<>();
	private final Map<UUID, Integer> shownSize = new HashMap<>();
	private final Map<UUID, Long> spawnedAt = new HashMap<>();
	private long tick;
	private final java.util.List<Mob> orphans = new java.util.ArrayList<>();
	private java.util.List<Integer> lastSentIds = java.util.List.of();

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
		if (!orphans.isEmpty()) {
			for (Mob orphan : orphans) {
				if (!orphan.isRemoved()) {
					orphan.ejectPassengers();
					orphan.discard();
				}
			}
			orphans.clear();
		}
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
		}
		for (UUID id : new HashSet<>(byPlayer.keySet())) {
			if (!keep.contains(id)) {
				remove(server, id);
			}
		}
		sendMap(server);
	}

	/**
	 * Allen Clients sagen, welches Kostüm zu wem gehört. Die Clients setzen das Kostüm dann selbst jeden Tick
	 * genau auf den Spieler – so hängt es nicht hinterher. Und das eigene wird in der Ich-Perspektive ausgeblendet.
	 */
	private void sendMap(MinecraftServer server) {
		java.util.List<Integer> ids = new java.util.ArrayList<>();
		java.util.List<String> players = new java.util.ArrayList<>();
		for (Map.Entry<UUID, Mob> entry : byPlayer.entrySet()) {
			ids.add(entry.getValue().getId());
			players.add(entry.getKey().toString());
		}
		boolean changed = !ids.equals(lastSentIds);
		if (!changed && tick % 20 != 0) {
			return;
		}
		lastSentIds = ids;
		DisguiseMapPayload payload = new DisguiseMapPayload(ids, players);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, DisguiseMapPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	/** Das Kostüm eines Spielers (darauf sitzen auch die Reiter). */
	public Mob of(ServerPlayer player) {
		Mob mob = byPlayer.get(player.getUUID());
		return mob == null || mob.isRemoved() ? null : mob;
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
		mob.setPortalCooldown(200);   // Kostüme gehen nie selbst durch Portale
		for (Entity passenger : mob.getPassengers()) {
			mob.positionRider(passenger);
		}
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
		if (entity instanceof Mob mob && byPlayer.get(owners.getOrDefault(mob.getUUID(), NOBODY)) != mob && mob.isNoAi() && mob.isSilent()
				&& mob.isNoGravity() && mob.isCustomNameVisible() && mob.getTeam() != null
				&& TEAM.equals(mob.getTeam().getName())) {
			// erst nach dem Laden (im nächsten Tick) entfernen
			orphans.add(mob);
		}
	}
}
