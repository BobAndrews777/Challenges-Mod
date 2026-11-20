package de.bob.challenges.mobs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import de.bob.challenges.ChallengeManager;
import de.bob.challenges.ChallengeSettings;
import de.bob.challenges.split.Mirrors;

/**
 * Die Mob-Challenge: Beim Timer-Start wird jeder nacheinander in einen zufälligen Mob verwandelt
 * und bekommt dessen Leben, Größe, Stärken, Schwächen und Fähigkeiten.
 */
public final class MobChallenge {
	private static final MobChallenge INSTANCE = new MobChallenge();

	private final MobAbilities abilities = new MobAbilities(this);
	private final ArrayDeque<UUID> transformQueue = new ArrayDeque<>();
	private int transformDelay = 0;
	/** Spieler, deren Werte wir verändert haben (zum Zurücksetzen) */
	private final Set<UUID> touched = new HashSet<>();
	/** Eisengolem: letzte erlaubte Lebenspunkte (Heilen nur mit Eisen) */
	private final Map<UUID, Float> golemHealth = new HashMap<>();
	private final Map<UUID, MobStatePayload> lastState = new HashMap<>();
	/** Magma Cube: 3 = groß, 2 = mittel, 1 = klein */
	private final Map<UUID, Integer> magmaSize = new HashMap<>();
	/** Reitbare Mobs mit Sattel */
	private final Set<UUID> saddled = new HashSet<>();
	/** Magma-Hüpfen und Kamel-Dash: Tick, ab dem es wieder geht */
	private final Map<UUID, Long> moveCooldown = new HashMap<>();
	private final Map<UUID, Boolean> lastJumpInput = new HashMap<>();
	/** Blaze: verbleibende Flugzeit in Ticks */
	private final Map<UUID, Integer> blazeStamina = new HashMap<>();
	private long tick = 0;

	private MobChallenge() {
	}

	public static MobChallenge get() {
		return INSTANCE;
	}

	ChallengeSettings s() {
		return ChallengeManager.get().settings();
	}

	public MobAbilities abilities() {
		return abilities;
	}

	public boolean enabled() {
		return s().mobMode;
	}

	/** Welcher Mob ist dieser Spieler? null = keiner. */
	public MobKind kindOf(Player player) {
		if (!s().mobMode) {
			return null;
		}
		String name = s().mobAssignments.get(player.getUUID().toString());
		return name == null ? null : MobKind.byName(name);
	}

	// ------------------------------------------------------------------
	// Verwandlung
	// ------------------------------------------------------------------

	/** Timer startet: alle ohne Mob kommen nacheinander dran. */
	public void onTimerStart(MinecraftServer server) {
		if (!s().mobMode) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isSpectator() && kindOf(player) == null && !transformQueue.contains(player.getUUID())) {
				transformQueue.add(player.getUUID());
			}
		}
		transformDelay = 40;
	}

	public void onJoin(MinecraftServer server, ServerPlayer player) {
		if (s().mobMode && ChallengeManager.get().isRunning() && kindOf(player) == null && !player.isSpectator()) {
			transformQueue.add(player.getUUID());
		}
	}

	private MobKind randomFreeKind() {
		Set<String> used = new HashSet<>(s().mobAssignments.values());
		List<MobKind> pool = new ArrayList<>();
		for (MobKind kind : MobKind.values()) {
			if (kind.ready && !used.contains(kind.name())) {
				pool.add(kind);
			}
		}
		if (pool.isEmpty()) {
			for (MobKind kind : MobKind.values()) {
				if (kind.ready) {
					pool.add(kind);
				}
			}
		}
		return pool.get(ChallengeManager.get().random().nextInt(pool.size()));
	}

	public void transform(MinecraftServer server, ServerPlayer player, MobKind kind) {
		s().mobAssignments.put(player.getUUID().toString(), kind.name());
		touched.add(player.getUUID());
		golemHealth.remove(player.getUUID());
		magmaSize.remove(player.getUUID());
		saddled.remove(player.getUUID());
		blazeStamina.remove(player.getUUID());
		player.ejectPassengers();
		ChallengeManager.get().save();
		apply(player, kind);
		player.setHealth(player.getMaxHealth());

		Component title = Component.literal(kind.displayName).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			other.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
			other.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(player.getName().getString() + " ist jetzt")
					.withStyle(ChatFormatting.WHITE)));
			other.connection.send(new ClientboundSetTitleTextPacket(title));
		}
		playSound(player, "minecraft:entity.zombie_villager.converted", 1.0f);
		StringBuilder info = new StringBuilder(kind.hearts + " Herzen");
		if (kind.ability1 != null) {
			info.append(" · R: ").append(kind.ability1);
		}
		if (kind.ability2 != null) {
			info.append(" · G: ").append(kind.ability2);
		}
		ChallengeManager.broadcast(server, Component.literal(player.getName().getString() + " ist jetzt ein " + kind.displayName + "! ")
				.withStyle(ChatFormatting.GOLD).append(Component.literal("(" + info + ")").withStyle(ChatFormatting.GRAY)));
	}

	/** Alles zurück: keine Mobs mehr, normale Werte. */
	public void reset(MinecraftServer server) {
		s().mobAssignments.clear();
		transformQueue.clear();
		golemHealth.clear();
		magmaSize.clear();
		saddled.clear();
		blazeStamina.clear();
		abilities.reset();
		if (server != null) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (touched.contains(player.getUUID())) {
					restore(player);
				}
			}
		}
		touched.clear();
	}

	// ------------------------------------------------------------------
	// Werte anwenden
	// ------------------------------------------------------------------

	private static void setBase(AttributeInstance attribute, double value) {
		if (attribute != null && Math.abs(attribute.getBaseValue() - value) > 0.0001) {
			attribute.setBaseValue(value);
		}
	}

	private static void infiniteEffect(ServerPlayer player, Holder<MobEffect> effect) {
		infiniteEffect(player, effect, 0);
	}

	private static void infiniteEffect(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
		MobEffectInstance current = player.getEffect(effect);
		if (current == null || !current.isInfiniteDuration() || current.getAmplifier() != amplifier) {
			if (current != null) {
				player.removeEffect(effect);
			}
			player.addEffect(new MobEffectInstance(effect, -1, amplifier, true, false, true));
		}
	}

	/** Magma Cube: Größe 3/2/1 → Leben, Größe und Sprunghöhe */
	int magmaSize(ServerPlayer player) {
		return magmaSize.getOrDefault(player.getUUID(), 3);
	}

	private static void removeInfinite(ServerPlayer player, Holder<MobEffect> effect) {
		MobEffectInstance current = player.getEffect(effect);
		if (current != null && current.isInfiniteDuration()) {
			player.removeEffect(effect);
		}
	}

	private void apply(ServerPlayer player, MobKind kind) {
		double maxHealth = kind.hearts * 2.0;
		double scale = kind.scale();
		double jump = kind.jumpStrength();
		if (kind == MobKind.MAGMA_CUBE) {
			int size = magmaSize(player);
			maxHealth = size == 3 ? 16.0 : size == 2 ? 4.0 : 1.0;
			scale = size == 3 ? 2.04 / 1.8 : size == 2 ? 1.02 / 1.8 : 0.52 / 1.8;
			jump = size == 3 ? 0.85 : size == 2 ? 0.62 : 0.42; // 4 / 2 / 1 Block(e)
		}
		setBase(player.getAttribute(Attributes.MAX_HEALTH), maxHealth);
		setBase(player.getAttribute(Attributes.SCALE), scale);
		setBase(player.getAttribute(Attributes.JUMP_STRENGTH), jump);
		setBase(player.getAttribute(Attributes.SAFE_FALL_DISTANCE), kind.safeFallDistance());
		setBase(player.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER), kind.fallDamageMultiplier());
		boolean rolled = abilities.isRolledUp(player);
		boolean noWalk = kind.has(MobKind.Flags.NO_WALK) || rolled || kind == MobKind.MAGMA_CUBE;
		setBase(player.getAttribute(Attributes.MOVEMENT_SPEED), noWalk ? 0.0 : 0.1);
		if (rolled) {
			setBase(player.getAttribute(Attributes.JUMP_STRENGTH), 0.0);
		}
		if (kind != MobKind.SILVERFISH) {
			setBase(player.getAttribute(Attributes.BLOCK_BREAK_SPEED), kind.breakSpeed());
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}

		if (kind.has(MobKind.Flags.WATER_BREATHING)) infiniteEffect(player, MobEffects.WATER_BREATHING);
		else removeInfinite(player, MobEffects.WATER_BREATHING);
		if (kind.has(MobKind.Flags.FIRE_IMMUNE)) infiniteEffect(player, MobEffects.FIRE_RESISTANCE);
		else removeInfinite(player, MobEffects.FIRE_RESISTANCE);
		if (kind.has(MobKind.Flags.DARKNESS)) infiniteEffect(player, MobEffects.DARKNESS);
		else removeInfinite(player, MobEffects.DARKNESS);

		// Abbau-Tempo nach Größe: winzig = Abbaulähmung, groß = Eile
		if (kind != MobKind.SILVERFISH) {
			int size = kind.sizeClass();
			if (size == 0) infiniteEffect(player, MobEffects.MINING_FATIGUE, 0);
			else removeInfinite(player, MobEffects.MINING_FATIGUE);
			if (size == 3) infiniteEffect(player, MobEffects.HASTE, 1);
			else if (size == 4) infiniteEffect(player, MobEffects.HASTE, 3);
			else removeInfinite(player, MobEffects.HASTE);
		}

		boolean fly = kind.has(MobKind.Flags.FLY) || player.isCreative() || player.isSpectator()
				|| (kind == MobKind.BLAZE && blazeStamina.getOrDefault(player.getUUID(), BLAZE_FLIGHT) > 0);
		if (player.getAbilities().mayfly != fly) {
			player.getAbilities().mayfly = fly;
			if (!fly) {
				player.getAbilities().flying = false;
			}
			player.onUpdateAbilities();
		}
	}

	/** Werte sofort neu anwenden (z. B. beim Einrollen). */
	void applyNow(ServerPlayer player) {
		MobKind kind = kindOf(player);
		if (kind != null) {
			apply(player, kind);
		}
	}

	private void restore(ServerPlayer player) {
		setBase(player.getAttribute(Attributes.MAX_HEALTH), 20.0);
		setBase(player.getAttribute(Attributes.SCALE), 1.0);
		setBase(player.getAttribute(Attributes.JUMP_STRENGTH), 0.42);
		setBase(player.getAttribute(Attributes.SAFE_FALL_DISTANCE), 3.0);
		setBase(player.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER), 1.0);
		setBase(player.getAttribute(Attributes.MOVEMENT_SPEED), 0.1);
		setBase(player.getAttribute(Attributes.BLOCK_BREAK_SPEED), 1.0);
		removeInfinite(player, MobEffects.WATER_BREATHING);
		removeInfinite(player, MobEffects.FIRE_RESISTANCE);
		removeInfinite(player, MobEffects.DARKNESS);
		removeInfinite(player, MobEffects.MINING_FATIGUE);
		removeInfinite(player, MobEffects.HASTE);
		player.ejectPassengers();
		boolean fly = player.isCreative() || player.isSpectator();
		if (player.getAbilities().mayfly != fly) {
			player.getAbilities().mayfly = fly;
			player.getAbilities().flying = player.getAbilities().flying && fly;
			player.onUpdateAbilities();
		}
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (Mirrors.isMarked(player.getInventory().getItem(i))) {
				player.getInventory().setItem(i, ItemStack.EMPTY);
			}
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	// ------------------------------------------------------------------
	// Tick
	// ------------------------------------------------------------------

	public void tick(MinecraftServer server) {
		tick++;
		if (!s().mobMode) {
			if (!touched.isEmpty()) {
				reset(server);
			}
			return;
		}
		boolean running = ChallengeManager.get().isRunning();

		// Nacheinander verwandeln
		if (running && !transformQueue.isEmpty() && --transformDelay <= 0) {
			UUID next = transformQueue.poll();
			ServerPlayer player = server.getPlayerList().getPlayer(next);
			if (player != null && kindOf(player) == null) {
				transform(server, player, randomFreeKind());
			}
			transformDelay = 60;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			MobKind kind = kindOf(player);
			if (kind == null) {
				if (touched.remove(player.getUUID())) {
					restore(player);
				}
				continue;
			}
			touched.add(player.getUUID());
			if (tick % 10 == 0) {
				apply(player, kind);
				lockInventory(player, kind);
			}
			if (running) {
				tickWeakness(player, kind);
			}
			if (kind == MobKind.IRON_GOLEM) {
				tickGolem(player);
			}
			if (kind == MobKind.SILVERFISH && tick % 2 == 0) {
				abilities.tickSilverfishMining(player);
			}
			if (running) {
				tickMovement(player, kind);
			}
		}

		if (tick % 5 == 0) {
			tickRelations(server);
		}
		abilities.tick(server);
		if (tick % 5 == 0) {
			sendStates(server);
		}
	}

	/** Wasser/Regen schadet (Enderman, Shulker). */
	private void tickWeakness(ServerPlayer player, MobKind kind) {
		if (kind.has(MobKind.Flags.WATER_DAMAGE) && tick % 10 == 0 && !player.isCreative()) {
			ServerLevel level = player.level();
			if (player.isInWater() || level.isRainingAt(player.blockPosition())) {
				player.hurtServer(level, level.damageSources().drown(), 1.0f);
			}
		}
	}

	/** Eisengolem regeneriert nicht von selbst – nur Eisen heilt ihn. */
	private void tickGolem(ServerPlayer player) {
		float health = player.getHealth();
		Float allowed = golemHealth.get(player.getUUID());
		if (allowed != null && health > allowed + 0.001f) {
			player.setHealth(allowed);
			health = allowed;
		}
		golemHealth.put(player.getUUID(), health);
	}

	/**
	 * Rechtsklick auf einen Mob-Spieler: Eisengolem heilen, Sattel auflegen, aufsitzen,
	 * Esel-Kiste öffnen (schleichen + Rechtsklick).
	 */
	public InteractionResult onUseEntity(Player player, InteractionHand hand, Entity target) {
		if (!(player instanceof ServerPlayer user) || !(target instanceof ServerPlayer mobPlayer) || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		MobKind kind = kindOf(mobPlayer);
		if (kind == null) {
			return InteractionResult.PASS;
		}
		if (kind == MobKind.IRON_GOLEM) {
			return healGolem(user, hand, mobPlayer);
		}
		ItemStack stack = user.getItemInHand(hand);
		String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (kind == MobKind.DONKEY && user.isShiftKeyDown()) {
			DonkeyChest.open(user, mobPlayer);
			return InteractionResult.SUCCESS;
		}
		if (kind.seats() > 0) {
			if (item.equals("saddle") && !saddled.contains(mobPlayer.getUUID())) {
				saddled.add(mobPlayer.getUUID());
				if (!user.isCreative()) {
					stack.shrink(1);
				}
				playSound(mobPlayer, "minecraft:entity.horse.saddle", 1.0f);
				ChallengeManager.broadcast(mobPlayer.level().getServer(), Component.literal(mobPlayer.getName().getString()
						+ " hat jetzt einen Sattel und kann geritten werden.").withStyle(ChatFormatting.GRAY));
				return InteractionResult.SUCCESS;
			}
			if (saddled.contains(mobPlayer.getUUID()) && stack.isEmpty()) {
				if (mobPlayer.getPassengers().size() >= kind.seats()) {
					user.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("Kein Platz mehr!").withStyle(ChatFormatting.RED)));
					return InteractionResult.FAIL;
				}
				if (user.startRiding(mobPlayer, true, true)) {
					return InteractionResult.SUCCESS;
				}
			}
			if (!saddled.contains(mobPlayer.getUUID()) && stack.isEmpty()) {
				user.connection.send(new ClientboundSetActionBarTextPacket(
						Component.literal("Zum Reiten braucht " + mobPlayer.getName().getString() + " erst einen Sattel.").withStyle(ChatFormatting.GRAY)));
			}
		}
		return InteractionResult.PASS;
	}

	/** Mitspieler heilen den Eisengolem per Rechtsklick mit einem Eisenbarren. */
	private InteractionResult healGolem(ServerPlayer player, InteractionHand hand, ServerPlayer golem) {
		ItemStack stack = player.getItemInHand(hand);
		if (!"iron_ingot".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath())) {
			return InteractionResult.PASS;
		}
		if (golem.getHealth() >= golem.getMaxHealth()) {
			return InteractionResult.PASS;
		}
		float healed = Math.min(golem.getMaxHealth(), golem.getHealth() + 25.0f);
		golem.setHealth(healed);
		golemHealth.put(golem.getUUID(), healed);
		if (!player.isCreative()) {
			stack.shrink(1);
		}
		playSound(golem, "minecraft:entity.iron_golem.repair", 1.0f);
		return InteractionResult.SUCCESS;
	}

	// ------------------------------------------------------------------
	// Inventar-Größe
	// ------------------------------------------------------------------

	/** Kleine Mobs haben weniger Platz: gesperrte Slots werden mit grauen Scheiben belegt. */
	private void lockInventory(ServerPlayer player, MobKind kind) {
		int allowed = kind.inventorySlots();
		Inventory inventory = player.getInventory();
		List<ItemStack> giveBack = new ArrayList<>();
		for (int index = 0; index < 36; index++) {
			ItemStack current = inventory.getItem(index);
			if (index < allowed) {
				if (Mirrors.isMarked(current)) {
					inventory.setItem(index, ItemStack.EMPTY);
				}
				continue;
			}
			if (!Mirrors.isMarked(current)) {
				if (!current.isEmpty()) {
					giveBack.add(current.copy());
				}
				inventory.setItem(index, Mirrors.placeholder(null));
			}
		}
		for (ItemStack stack : giveBack) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	// ------------------------------------------------------------------
	// Essen
	// ------------------------------------------------------------------

	private static boolean isFlower(String path) {
		return path.endsWith("_tulip") || Set.of("dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy",
				"cornflower", "lily_of_the_valley", "wither_rose", "torchflower", "sunflower", "lilac", "rose_bush", "peony",
				"pink_petals", "spore_blossom", "pitcher_plant", "open_eyeblossom", "closed_eyeblossom", "cactus_flower",
				"wildflowers").contains(path);
	}

	private static boolean allowedFood(MobKind kind, String path) {
		if (kind.foods == null) {
			return true;
		}
		if (kind.foods.contains("#flowers") && isFlower(path)) {
			return true;
		}
		return kind.foods.contains(path);
	}

	/** Nährwert für Dinge, die normalerweise kein Essen sind. */
	private static int customNutrition(String path) {
		return switch (path) {
			case "lava_bucket" -> 8;
			case "magma_block" -> 4;
			case "wheat", "magma_cream", "gunpowder" -> 3;
			default -> 2;
		};
	}

	/** Aus UseItemCallback: Essens-Regeln des Mobs. */
	public InteractionResult onUseItem(Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		MobKind kind = kindOf(player);
		if (kind == null) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (stack.isEmpty() || Mirrors.isMarked(stack)) {
			return InteractionResult.PASS;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		boolean isFood = stack.has(DataComponents.FOOD);
		boolean allowed = allowedFood(kind, path);

		if (isFood && !allowed) {
			serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(
					Component.literal("Als " + kind.displayName + " kannst du das nicht essen!").withStyle(ChatFormatting.RED)));
			return InteractionResult.FAIL;
		}
		if (!isFood && allowed && kind.foods != null) {
			if (!player.getFoodData().needsFood()) {
				return InteractionResult.PASS;
			}
			player.getFoodData().eat(customNutrition(path), 0.6f);
			if (!player.isCreative()) {
				if (path.equals("lava_bucket")) {
					player.setItemInHand(hand, new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("bucket"))));
				} else {
					stack.shrink(1);
				}
			}
			playSound(serverPlayer, "minecraft:entity.generic.eat", 1.0f);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	// ------------------------------------------------------------------
	// Schaden
	// ------------------------------------------------------------------

	/** Aus ALLOW_DAMAGE: eigene Explosion schadet dem Creeper nicht, Golem-Heilung merken. */
	public boolean allowDamage(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer player)) {
			return true;
		}
		MobKind kind = kindOf(player);
		if (kind == MobKind.CREEPER && source.is(DamageTypeTags.IS_EXPLOSION) && source.getEntity() == player) {
			return false;
		}
		if (kind == MobKind.ARMADILLO && abilities.isRolledUp(player)) {
			return false;
		}
		if (kind != null && source.getEntity() instanceof Mob mob && !mayTarget(mob, player)) {
			mob.setTarget(null);
			return false;
		}
		return true;
	}

	/** Magma Cube teilt sich bei tödlichem Schaden in die nächstkleinere Stufe. */
	public boolean allowDeath(LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player) || kindOf(player) != MobKind.MAGMA_CUBE) {
			return true;
		}
		int size = magmaSize(player);
		if (size <= 1) {
			return true;
		}
		magmaSize.put(player.getUUID(), size - 1);
		apply(player, MobKind.MAGMA_CUBE);
		player.setHealth(player.getMaxHealth());
		playSound(player, "minecraft:entity.magma_cube.squish", 1.0f);
		ChallengeManager.broadcast(player.level().getServer(), Component.literal(player.getName().getString()
				+ " ist jetzt ein " + (size - 1 == 2 ? "mittlerer" : "kleiner") + " Magma Cube!").withStyle(ChatFormatting.GOLD));
		return false;
	}

	// ------------------------------------------------------------------
	// Freund und Feind
	// ------------------------------------------------------------------

	private static final Set<MobKind> MONSTERS = Set.of(MobKind.CREEPER, MobKind.ENDERMAN, MobKind.BREEZE, MobKind.BLAZE,
			MobKind.ELDER_GUARDIAN, MobKind.GHAST, MobKind.MAGMA_CUBE, MobKind.SILVERFISH, MobKind.WARDEN, MobKind.EVOKER,
			MobKind.SHULKER);

	private static String typeOf(Entity entity) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
	}

	/** Darf dieser Mob den Spieler als Ziel nehmen? (aus dem Mob-Mixin und für den Schaden) */
	public boolean mayTarget(Mob mob, LivingEntity target) {
		if (!(target instanceof ServerPlayer player)) {
			return true;
		}
		MobKind kind = kindOf(player);
		if (kind == null) {
			return true;
		}
		return !typeOf(mob).equals(kind.entityId) && !abilities.isMinionOf(mob, player);
	}

	/**
	 * Freund und Feind:
	 * - Mobs der eigenen Art greifen nicht an
	 * - Eisengolems jagen Spieler, die Monster sind
	 * - Creeper und Phantome fliehen vor der Katze, Spinnen vor dem Gürteltier
	 */
	private void tickRelations(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			MobKind kind = kindOf(player);
			if (kind == null || player.isSpectator()) {
				continue;
			}
			AABB area = player.getBoundingBox().inflate(24);
			for (Mob mob : player.level().getEntitiesOfClass(Mob.class, area, Mob::isAlive)) {
				String type = typeOf(mob);
				if (mob.getTarget() == player && !mayTarget(mob, player)) {
					mob.setTarget(null);
				}
				if (MONSTERS.contains(kind) && (type.equals("iron_golem") || type.equals("snow_golem"))
						&& mob.getTarget() == null && mob.distanceToSqr(player) < 16 * 16) {
					mob.setTarget(player);
				}
				boolean afraid = (kind == MobKind.CAT && (type.equals("creeper") || type.equals("phantom")))
						|| (kind == MobKind.ARMADILLO && (type.equals("spider") || type.equals("cave_spider")));
				if (afraid && mob.distanceToSqr(player) < 8 * 8) {
					if (mob.getTarget() == player) {
						mob.setTarget(null);
					}
					double dx = mob.getX() - player.getX();
					double dz = mob.getZ() - player.getZ();
					double length = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
					mob.setDeltaMovement(dx / length * 0.4, mob.getDeltaMovement().y, dz / length * 0.4);
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// Bewegung: Magma-Hüpfen, Kamel-Dash, Blaze-Flug, Schreiter-Reiter
	// ------------------------------------------------------------------

	private static final int BLAZE_FLIGHT = 60;

	private void tickMovement(ServerPlayer player, MobKind kind) {
		UUID id = player.getUUID();
		var input = player.getLastClientInput();
		switch (kind) {
			case MAGMA_CUBE -> {
				// Fortbewegung nur durch Hüpfen: Bewegungstaste auf dem Boden = Hüpfer in diese Richtung
				boolean moving = input.forward() || input.backward() || input.left() || input.right();
				if (player.onGround() && moving && tick >= moveCooldown.getOrDefault(id, 0L)) {
					double yaw = Math.toRadians(player.getYRot());
					double forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
					double strafe = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
					double x = -Math.sin(yaw) * forward + Math.cos(yaw) * strafe;
					double z = Math.cos(yaw) * forward + Math.sin(yaw) * strafe;
					double length = Math.max(1.0, Math.sqrt(x * x + z * z));
					int size = magmaSize(player);
					double push = size == 3 ? 0.55 : size == 2 ? 0.45 : 0.35;
					double up = size == 3 ? 0.65 : size == 2 ? 0.55 : 0.42;
					player.setDeltaMovement(x / length * push, up, z / length * push);
					player.hurtMarked = true;
					moveCooldown.put(id, tick + 12);
				}
			}
			case CAMEL -> {
				// Leertaste = Dash nach vorne (Abklingzeit wie beim echten Kamel)
				boolean jump = input.jump();
				boolean wasJump = lastJumpInput.getOrDefault(id, false);
				lastJumpInput.put(id, jump);
				if (jump && !wasJump && player.onGround() && tick >= moveCooldown.getOrDefault(id, 0L)) {
					var look = player.getLookAngle();
					double length = Math.max(0.01, Math.sqrt(look.x * look.x + look.z * look.z));
					player.setDeltaMovement(look.x / length * 1.4, 0.4, look.z / length * 1.4);
					player.hurtMarked = true;
					playSound(player, "minecraft:entity.camel.dash", 1.0f);
					moveCooldown.put(id, tick + 55);
				}
			}
			case BLAZE -> {
				// Kurz fliegen (3 Sekunden), danach langsam nach unten gleiten; auf dem Boden lädt es sich auf
				int stamina = blazeStamina.getOrDefault(id, BLAZE_FLIGHT);
				if (player.getAbilities().flying) {
					stamina = Math.max(0, stamina - 1);
					if (stamina == 0) {
						player.getAbilities().flying = false;
						player.getAbilities().mayfly = false;
						player.onUpdateAbilities();
					}
				} else if (player.onGround()) {
					stamina = Math.min(BLAZE_FLIGHT, stamina + 2);
				}
				blazeStamina.put(id, stamina);
				if (!player.onGround() && !player.getAbilities().flying && player.getDeltaMovement().y < 0) {
					player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, true, false, false));
				}
			}
			case STRIDER -> {
				// Reiter auf dem Schreiter verbrennen nicht
				for (Entity passenger : player.getPassengers()) {
					if (passenger instanceof ServerPlayer rider) {
						rider.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, true, false, true));
					}
				}
			}
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------
	// Fähigkeiten und Anzeige
	// ------------------------------------------------------------------

	public void onAbility(ServerPlayer player, int ability) {
		MobKind kind = kindOf(player);
		if (kind == null || !ChallengeManager.get().isRunning()) {
			return;
		}
		abilities.use(player, kind, ability);
	}

	private void sendStates(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!ServerPlayNetworking.canSend(player, MobStatePayload.TYPE)) {
				continue;
			}
			MobKind kind = kindOf(player);
			MobStatePayload state = kind == null
					? new MobStatePayload("", "", 0, "", 0)
					: new MobStatePayload(kind.displayName,
							kind.ability1 == null ? "" : kind.ability1, abilities.cooldown(player, 1),
							kind.ability2 == null ? "" : kind.ability2, abilities.cooldown(player, 2));
			if (!state.equals(lastState.get(player.getUUID()))) {
				lastState.put(player.getUUID(), state);
				ServerPlayNetworking.send(player, state);
			}
		}
	}

	static void playSound(ServerPlayer player, String sound, float pitch) {
		Identifier id = Identifier.parse(sound);
		if (BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
			SoundEvent event = BuiltInRegistries.SOUND_EVENT.getValue(id);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), event, SoundSource.PLAYERS, 1.0f, pitch);
		}
	}

	public String statusText(MinecraftServer server) {
		StringBuilder sb = new StringBuilder("Mob-Challenge: " + (s().mobMode ? "AN" : "AUS"));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			MobKind kind = kindOf(player);
			sb.append("\n").append(player.getName().getString()).append(": ").append(kind == null ? "noch kein Mob" : kind.displayName);
		}
		return sb.toString();
	}
}
