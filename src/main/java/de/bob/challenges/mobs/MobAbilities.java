package de.bob.challenges.mobs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Die Fähigkeiten der Mobs (Taste R = Fähigkeit 1, Taste G = Fähigkeit 2).
 * Abklingzeiten wie bei den echten Mobs.
 */
public final class MobAbilities {
	private final MobChallenge challenge;
	/** Spieler → Tick, ab dem Fähigkeit 1/2 wieder bereit ist */
	private final Map<UUID, long[]> readyAt = new HashMap<>();
	/** Beschworene Helfer → Besitzer */
	private final Map<UUID, UUID> minions = new HashMap<>();
	private final Map<UUID, ServerLevel> minionLevels = new HashMap<>();
	/** Gürteltier: eingerollt bis Tick */
	private final Map<UUID, Long> rolledUntil = new HashMap<>();
	/** Blaze: noch abzufeuernde Feuerbälle der Salve */
	private final Map<UUID, Integer> blazeBurst = new HashMap<>();
	/** Ziege: Dash läuft bis Tick, schon getroffene Ziele */
	private final Map<UUID, Long> goatDashUntil = new HashMap<>();
	private final Map<UUID, Set<UUID>> goatHit = new HashMap<>();
	/** Elder Guardian: laufende Laser (Spieler → Ziel, Ende) */
	private record Laser(UUID target, ServerLevel level, long fireAt) {
	}
	private final Map<UUID, Laser> lasers = new HashMap<>();
	/** Helfer-Typ (für Magma-Minis: "magma_2" / "magma_1") */
	private final Map<UUID, String> minionKinds = new HashMap<>();
	private long tick = 0;

	MobAbilities(MobChallenge challenge) {
		this.challenge = challenge;
	}

	void reset() {
		readyAt.clear();
		rolledUntil.clear();
		blazeBurst.clear();
		goatDashUntil.clear();
		goatHit.clear();
		lasers.clear();
	}

	public int cooldown(ServerPlayer player, int ability) {
		long[] ready = readyAt.get(player.getUUID());
		if (ready == null) {
			return 0;
		}
		return (int) Math.max(0, ready[ability - 1] - tick);
	}

	private boolean ready(ServerPlayer player, int ability) {
		if (cooldown(player, ability) > 0) {
			player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("Noch "
					+ (cooldown(player, ability) + 19) / 20 + " s Abklingzeit").withStyle(ChatFormatting.GRAY)));
			return false;
		}
		return true;
	}

	private void startCooldown(ServerPlayer player, int ability, int ticks) {
		readyAt.computeIfAbsent(player.getUUID(), k -> new long[2])[ability - 1] = tick + ticks;
	}

	boolean isRolledUp(ServerPlayer player) {
		Long until = rolledUntil.get(player.getUUID());
		return until != null && until > tick;
	}

	boolean isMinionOf(Mob mob, ServerPlayer player) {
		return player.getUUID().equals(minions.get(mob.getUUID()));
	}

	// ------------------------------------------------------------------

	void use(ServerPlayer player, MobKind kind, int ability) {
		if (!ready(player, ability)) {
			return;
		}
		switch (kind) {
			case PIG -> {
				if (ability == 1) {
					MobChallenge.playSound(player, "minecraft:entity.pig.ambient", 1.0f);
					startCooldown(player, 1, 20);
				}
			}
			case CREEPER -> {
				if (ability == 1) {
					MobChallenge.playSound(player, "minecraft:entity.creeper.primed", 1.0f);
					player.level().explode(player, player.getX(), player.getY() + 0.5, player.getZ(), 3.0f, Level.ExplosionInteraction.MOB);
					startCooldown(player, 1, 7 * 20);
				}
			}
			case ENDERMAN -> {
				if (ability == 1 && teleportLooking(player, 32)) {
					startCooldown(player, 1, 20);
				}
			}
			case SHULKER -> {
				if (ability == 1 && teleportLooking(player, 16)) {
					startCooldown(player, 1, 20);
				} else if (ability == 2) {
					shulkerBullet(player);
				}
			}
			case BREEZE -> {
				if (ability == 1) {
					shoot(player, "wind_charge", 1.5);
					MobChallenge.playSound(player, "minecraft:entity.breeze.shoot", 1.0f);
					startCooldown(player, 1, 30);
				}
			}
			case BLAZE -> {
				if (ability == 1) {
					blazeBurst.put(player.getUUID(), 3);
					startCooldown(player, 1, 5 * 20);
				}
			}
			case GHAST -> {
				if (ability == 1) {
					shoot(player, "fireball", 1.0);
					MobChallenge.playSound(player, "minecraft:entity.ghast.shoot", 1.0f);
					startCooldown(player, 1, 3 * 20);
				}
			}
			case EVOKER -> {
				if (ability == 1) {
					evokerFangs(player);
					startCooldown(player, 1, 2 * 20);
				} else if (ability == 2) {
					if (summon(player, "vex", 3, 3)) {
						MobChallenge.playSound(player, "minecraft:entity.evoker.prepare_summon", 1.0f);
						startCooldown(player, 2, 17 * 20);
					}
				}
			}
			case SILVERFISH -> {
				if (ability == 1 && summon(player, "silverfish", 1, 20)) {
					MobChallenge.playSound(player, "minecraft:entity.silverfish.ambient", 1.0f);
					startCooldown(player, 1, 10 * 20);
				}
			}
			case ARMADILLO -> {
				if (ability == 1) {
					rolledUntil.put(player.getUUID(), tick + 5 * 20);
					challenge.applyNow(player);
					MobChallenge.playSound(player, "minecraft:entity.armadillo.roll", 1.0f);
					startCooldown(player, 1, 8 * 20);
				}
			}
			case GOAT -> {
				if (ability == 1) {
					var look = player.getLookAngle();
					double length = Math.max(0.01, Math.sqrt(look.x * look.x + look.z * look.z));
					player.setDeltaMovement(look.x / length * 1.6, 0.2, look.z / length * 1.6);
					player.hurtMarked = true;
					goatDashUntil.put(player.getUUID(), tick + 12);
					goatHit.put(player.getUUID(), new java.util.HashSet<>());
					MobChallenge.playSound(player, "minecraft:entity.goat.long_jump", 1.0f);
					startCooldown(player, 1, 5 * 20);
				}
			}
			case BEE -> {
				if (ability == 1 && summon(player, "bee", 10, 10)) {
					MobChallenge.playSound(player, "minecraft:entity.bee.loop_aggressive", 1.0f);
					startCooldown(player, 1, 30 * 20);
				}
			}
			case WARDEN -> {
				if (ability == 1) {
					sonicBoom(player);
					startCooldown(player, 1, 40);
				}
			}
			case ELDER_GUARDIAN -> {
				if (ability == 1) {
					LivingEntity target = lookTarget(player, 16);
					if (target == null) {
						player.connection.send(new ClientboundSetActionBarTextPacket(
								Component.literal("Schau ein Ziel an!").withStyle(ChatFormatting.GRAY)));
					} else {
						lasers.put(player.getUUID(), new Laser(target.getUUID(), player.level(), tick + 40));
						MobChallenge.playSound(player, "minecraft:entity.elder_guardian.curse", 1.0f);
						startCooldown(player, 1, 80);
					}
				}
			}
			case MAGMA_CUBE -> {
				if (ability == 1) {
					summonMagma(player);
				}
			}
			default -> {
				if ((ability == 1 ? kind.ability1 : kind.ability2) != null) {
					player.connection.send(new ClientboundSetActionBarTextPacket(
							Component.literal("Diese Fähigkeit kommt mit dem Aussehen (Etappe 3)!").withStyle(ChatFormatting.GRAY)));
				}
			}
		}
	}

	void tick(MinecraftServer server) {
		tick++;
		// Blaze-Salve: 3 Feuerbälle kurz hintereinander
		if (tick % 6 == 0 && !blazeBurst.isEmpty()) {
			Iterator<Map.Entry<UUID, Integer>> it = blazeBurst.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<UUID, Integer> entry = it.next();
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player != null) {
					shoot(player, "small_fireball", 1.2);
					MobChallenge.playSound(player, "minecraft:entity.blaze.shoot", 1.0f);
				}
				if (player == null || entry.getValue() <= 1) {
					it.remove();
				} else {
					entry.setValue(entry.getValue() - 1);
				}
			}
		}
		tickGoatDash(server);
		tickLasers(server);
		if (tick % 20 == 0) {
			tickMinions(server);
		}
	}

	// ------------------------------------------------------------------
	// Bausteine
	// ------------------------------------------------------------------

	private static Entity create(ServerLevel level, String id) {
		Identifier key = Identifier.withDefaultNamespace(id);
		if (!BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
			return null;
		}
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(key);
		return type.create(level, EntitySpawnReason.MOB_SUMMONED);
	}

	/** Geschoss in Blickrichtung abfeuern. */
	private static void shoot(ServerPlayer player, String id, double speed) {
		ServerLevel level = player.level();
		Entity entity = create(level, id);
		if (entity == null) {
			return;
		}
		Vec3 look = player.getLookAngle();
		Vec3 start = player.getEyePosition().add(look.scale(1.2 * Math.max(1.0, player.getScale())));
		entity.setPos(start.x, start.y, start.z);
		entity.setDeltaMovement(look.scale(speed));
		if (entity instanceof Projectile projectile) {
			projectile.setOwner(player);
		}
		level.addFreshEntity(entity);
	}

	/** Zielendes Schwebe-Geschoss: verfolgt das angeschaute Ziel oder das nächste Monster. */
	private void shulkerBullet(ServerPlayer player) {
		LivingEntity target = lookTarget(player, 32);
		if (target == null) {
			target = nearestMonster(player, 16);
		}
		if (target == null) {
			player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("Kein Ziel in der Nähe!").withStyle(ChatFormatting.GRAY)));
			return;
		}
		ServerLevel level = player.level();
		Entity probe = create(level, "shulker_bullet");
		Entity bullet = null;
		if (probe != null) {
			try {
				var constructor = probe.getClass().getConstructor(Level.class, LivingEntity.class, Entity.class,
						net.minecraft.core.Direction.Axis.class);
				bullet = (Entity) constructor.newInstance(level, player, target, net.minecraft.core.Direction.Axis.Y);
			} catch (ReflectiveOperationException e) {
				bullet = probe; // Notlösung: geradeaus
				bullet.setPos(player.getX(), player.getEyeY(), player.getZ());
				bullet.setDeltaMovement(player.getLookAngle().scale(0.8));
				if (bullet instanceof Projectile projectile) {
					projectile.setOwner(player);
				}
			}
		}
		if (bullet != null) {
			level.addFreshEntity(bullet);
			MobChallenge.playSound(player, "minecraft:entity.shulker.shoot", 1.0f);
			startCooldown(player, 2, 2 * 20);
		}
	}

	/** Ziel, auf das der Spieler schaut (keine Mitspieler). */
	static LivingEntity lookTarget(ServerPlayer player, double range) {
		Vec3 start = player.getEyePosition();
		Vec3 end = start.add(player.getLookAngle().scale(range));
		HitResult block = player.pick(range, 0.0f, false);
		if (block.getType() == HitResult.Type.BLOCK) {
			end = block.getLocation();
		}
		AABB area = player.getBoundingBox().expandTowards(player.getLookAngle().scale(range)).inflate(1.0);
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity entity : player.level().getEntities(player, area,
				e -> e instanceof LivingEntity && e.isAlive() && !(e instanceof Player))) {
			var hit = entity.getBoundingBox().inflate(0.3).clip(start, end);
			if (hit.isPresent()) {
				double distance = start.distanceToSqr(hit.get());
				if (distance < bestDistance) {
					bestDistance = distance;
					best = (LivingEntity) entity;
				}
			}
		}
		return best;
	}

	private LivingEntity nearestMonster(ServerPlayer player, double range) {
		Mob closest = null;
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range),
				m -> m.isAlive() && m.getType().getCategory() == MobCategory.MONSTER && !minions.containsKey(m.getUUID()))) {
			if (closest == null || mob.distanceToSqr(player) < closest.distanceToSqr(player)) {
				closest = mob;
			}
		}
		return closest;
	}

	/** Warden: Sonic Boom – trifft das erste Ziel in Blickrichtung, geht durch Rüstung. */
	private void sonicBoom(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 start = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		LivingEntity target = lookTarget(player, 20);
		double length = target == null ? 15 : Math.sqrt(start.distanceToSqr(target.getEyePosition()));
		for (double d = 1; d <= length; d += 1.0) {
			Vec3 pos = start.add(look.scale(d));
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.SONIC_BOOM, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
		}
		MobChallenge.playSound(player, "minecraft:entity.warden.sonic_boom", 1.0f);
		if (target != null) {
			target.hurtServer(level, level.damageSources().sonicBoom(player), 10.0f);
			target.knockback(2.5, -look.x, -look.z);
		}
	}

	private void tickGoatDash(MinecraftServer server) {
		if (goatDashUntil.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Long>> it = goatDashUntil.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Long> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || entry.getValue() < tick) {
				it.remove();
				goatHit.remove(entry.getKey());
				continue;
			}
			Set<UUID> hit = goatHit.computeIfAbsent(entry.getKey(), k -> new java.util.HashSet<>());
			for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(0.6),
					e -> e instanceof LivingEntity && e.isAlive())) {
				if (hit.add(entity.getUUID())) {
					LivingEntity living = (LivingEntity) entity;
					living.hurtServer(player.level(), player.level().damageSources().playerAttack(player), 6.0f);
					Vec3 look = player.getLookAngle();
					living.knockback(2.0, -look.x, -look.z);
					MobChallenge.playSound(player, "minecraft:entity.goat.ram_impact", 1.0f);
				}
			}
		}
	}

	private void tickLasers(MinecraftServer server) {
		if (lasers.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Laser>> it = lasers.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Laser> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Laser laser = entry.getValue();
			Entity targetEntity = laser.level().getEntity(laser.target());
			if (player == null || !(targetEntity instanceof LivingEntity target) || !target.isAlive()
					|| target.distanceToSqr(player) > 20 * 20) {
				it.remove();
				continue;
			}
			if (tick % 2 == 0) {
				Vec3 from = player.getEyePosition();
				Vec3 to = target.getEyePosition();
				Vec3 step = to.subtract(from);
				double length = step.length();
				for (double d = 0; d < length; d += 0.5) {
					Vec3 pos = from.add(step.scale(d / length));
					laser.level().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
				}
			}
			if (tick >= laser.fireAt()) {
				target.hurtServer(laser.level(), laser.level().damageSources().indirectMagic(player, player), 8.0f);
				MobChallenge.playSound(player, "minecraft:entity.guardian.attack", 1.0f);
				it.remove();
			}
		}
	}

	/** Magma Cube: Minis rufen – höchstens 4 mittlere und 8 kleine. */
	private void summonMagma(ServerPlayer player) {
		int medium = 0;
		int small = 0;
		for (Map.Entry<UUID, UUID> entry : minions.entrySet()) {
			if (entry.getValue().equals(player.getUUID())) {
				String kind = minionKinds.get(entry.getKey());
				if ("magma_2".equals(kind)) medium++;
				if ("magma_1".equals(kind)) small++;
			}
		}
		int size;
		if (medium < 4) {
			size = 2;
		} else if (small < 8) {
			size = 1;
		} else {
			player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("Du hast schon alle Minis!").withStyle(ChatFormatting.GRAY)));
			return;
		}
		ServerLevel level = player.level();
		Entity cube = create(level, "magma_cube");
		if (cube == null) {
			return;
		}
		try {
			var method = cube.getClass().getMethod("setSize", int.class, boolean.class);
			method.invoke(cube, size, true);
		} catch (ReflectiveOperationException ignored) {
			// dann eben in Standardgröße
		}
		cube.setPos(player.getX() + 1.0, player.getY(), player.getZ() + 1.0);
		level.addFreshEntity(cube);
		minions.put(cube.getUUID(), player.getUUID());
		minionLevels.put(cube.getUUID(), level);
		minionKinds.put(cube.getUUID(), "magma_" + size);
		MobChallenge.playSound(player, "minecraft:entity.magma_cube.jump", 1.0f);
		startCooldown(player, 1, 3 * 20);
	}

	/** Teleport dorthin, wo man hinschaut (Enderman, Shulker). */
	private static boolean teleportLooking(ServerPlayer player, double range) {
		ServerLevel level = player.level();
		HitResult hit = player.pick(range, 0.0f, false);
		Vec3 target = hit.getLocation();
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos above = blockHit.getBlockPos().relative(blockHit.getDirection());
			target = new Vec3(above.getX() + 0.5, above.getY(), above.getZ() + 0.5);
		}
		for (int up = 0; up <= 4; up++) {
			Vec3 candidate = target.add(0, up, 0);
			AABB box = player.getBoundingBox().move(candidate.subtract(player.position()));
			if (level.noCollision(player, box)) {
				MobChallenge.playSound(player, "minecraft:entity.enderman.teleport", 1.0f);
				player.teleportTo(level, candidate.x, candidate.y, candidate.z, Set.of(), player.getYRot(), player.getXRot(), true);
				MobChallenge.playSound(player, "minecraft:entity.enderman.teleport", 1.0f);
				return true;
			}
		}
		player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("Dort ist kein Platz!").withStyle(ChatFormatting.RED)));
		return false;
	}

	/** Evoker-Klauen in einer Reihe nach vorne. */
	private static void evokerFangs(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
		for (int i = 2; i <= 10; i++) {
			Vec3 pos = player.position().add(flat.scale(i * 1.25));
			Entity fangs = create(level, "evoker_fangs");
			if (fangs != null) {
				fangs.setPos(pos.x, Math.floor(player.getY()), pos.z);
				fangs.setYRot(player.getYRot());
				level.addFreshEntity(fangs);
			}
		}
		MobChallenge.playSound(player, "minecraft:entity.evoker.cast_spell", 1.0f);
	}

	/** Helfer beschwören, höchstens max gleichzeitig. */
	private boolean summon(ServerPlayer player, String id, int count, int max) {
		int alive = 0;
		for (UUID owner : minions.values()) {
			if (owner.equals(player.getUUID())) {
				alive++;
			}
		}
		if (alive >= max) {
			player.connection.send(new ClientboundSetActionBarTextPacket(
					Component.literal("Du hast schon " + max + " Helfer!").withStyle(ChatFormatting.GRAY)));
			return false;
		}
		ServerLevel level = player.level();
		for (int i = 0; i < Math.min(count, max - alive); i++) {
			Entity entity = create(level, id);
			if (entity == null) {
				return false;
			}
			double angle = level.getRandom().nextDouble() * Math.PI * 2;
			entity.setPos(player.getX() + Math.cos(angle) * 1.5, player.getY() + (id.equals("vex") ? 1.0 : 0.0),
					player.getZ() + Math.sin(angle) * 1.5);
			level.addFreshEntity(entity);
			minions.put(entity.getUUID(), player.getUUID());
			minionLevels.put(entity.getUUID(), level);
		}
		return true;
	}

	/** Helfer greifen nur Monster an, nie die Spieler. */
	private void tickMinions(MinecraftServer server) {
		List<UUID> dead = new ArrayList<>();
		for (Map.Entry<UUID, UUID> entry : minions.entrySet()) {
			ServerLevel level = minionLevels.get(entry.getKey());
			Entity entity = level == null ? null : level.getEntity(entry.getKey());
			if (!(entity instanceof Mob mob) || !mob.isAlive()) {
				dead.add(entry.getKey());
				continue;
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getValue());
			LivingEntity target = mob.getTarget();
			if (target instanceof Player || (target != null && minions.containsKey(target.getUUID()))) {
				mob.setTarget(null);
				target = null;
			}
			if (target == null && owner != null) {
				AABB area = owner.getBoundingBox().inflate(16);
				List<Mob> enemies = level.getEntitiesOfClass(Mob.class, area,
						m -> m.isAlive() && m.getType().getCategory() == MobCategory.MONSTER && !minions.containsKey(m.getUUID()));
				Mob closest = null;
				for (Mob enemy : enemies) {
					if (closest == null || enemy.distanceToSqr(owner) < closest.distanceToSqr(owner)) {
						closest = enemy;
					}
				}
				if (closest != null) {
					mob.setTarget(closest);
					try {
						// Bienen und andere neutrale Mobs müssen „wütend“ sein, um anzugreifen
						mob.getClass().getMethod("startPersistentAngerTimer").invoke(mob);
					} catch (ReflectiveOperationException ignored) {
					}
				}
			}
		}
		for (UUID id : dead) {
			minions.remove(id);
			minionLevels.remove(id);
			minionKinds.remove(id);
		}
	}

	/** Silberfisch baut Stein und Bruchstein sehr schnell ab, alles andere langsam (klein). */
	void tickSilverfishMining(ServerPlayer player) {
		HitResult hit = player.pick(5.0, 0.0f, false);
		double speed = 0.5;
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			String block = BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(blockHit.getBlockPos()).getBlock()).getPath();
			if (block.equals("stone") || block.equals("cobblestone") || block.equals("deepslate") || block.equals("cobbled_deepslate")) {
				speed = 6.0;
			}
		}
		var attribute = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
		if (attribute != null && Math.abs(attribute.getBaseValue() - speed) > 0.001) {
			attribute.setBaseValue(speed);
			if (speed > 1) {
				player.removeEffect(net.minecraft.world.effect.MobEffects.MINING_FATIGUE);
				player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HASTE, -1, 4, true, false, true));
			} else {
				player.removeEffect(net.minecraft.world.effect.MobEffects.HASTE);
				player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MINING_FATIGUE, -1, 0, true, false, true));
			}
		}
	}
}
