package de.bob.challenges;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import de.bob.challenges.split.SplitManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Prediction;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Herzstück der Mod: Timer, Ziel, Tod und alle Challenges.
 */
public final class ChallengeManager {
	private static final ChallengeManager INSTANCE = new ChallengeManager();

	/** Items, die nie als Zufalls-Item oder Zufalls-Drop auftauchen sollen. */
	private static final Set<String> ITEM_BLACKLIST = Set.of(
			"minecraft:air", "minecraft:barrier", "minecraft:light", "minecraft:debug_stick",
			"minecraft:knowledge_book", "minecraft:jigsaw", "minecraft:structure_block",
			"minecraft:structure_void", "minecraft:command_block", "minecraft:chain_command_block",
			"minecraft:repeating_command_block", "minecraft:command_block_minecart",
			"minecraft:test_block", "minecraft:test_instance_block");

	private ChallengeSettings settings = new ChallengeSettings();
	private final ForceChallenge force = new ForceChallenge();
	private final Random random = new Random();

	private boolean running = false;
	private long tickCounter = 0;

	private final List<Item> obtainableItems = new ArrayList<>();
	private final Map<Item, Item> dropMap = new HashMap<>();
	private final Map<UUID, Integer> lastJumps = new HashMap<>();
	private float sharedHealth = Float.NaN;

	private ChallengeManager() {
	}

	public static ChallengeManager get() {
		return INSTANCE;
	}

	public ChallengeSettings settings() {
		return settings;
	}

	public boolean isRunning() {
		return running;
	}

	public boolean isEnded() {
		return settings.ended;
	}

	public ForceChallenge force() {
		return force;
	}

	// ------------------------------------------------------------------
	// Laden, Speichern, Server-Start
	// ------------------------------------------------------------------

	public void load(boolean worldWasReset) {
		settings = ChallengeSettings.load();
		if (worldWasReset) {
			settings.timerTicks = 0;
			settings.ended = false;
			settings.dropSeed = new Random().nextLong();
			settings.playerColors.clear();
			settings.activeSplits.clear();
			settings.removedSplits.clear();
			settings.weaponOwners.clear();
			settings.toolOwners.clear();
			settings.jokersLeft = settings.splitJokers;
			settings.nextSplitAt = -1;
			settings.placeTurn = 0;
		}
		settings.save();
	}

	public void save() {
		settings.save();
	}

	public void onServerStarted(MinecraftServer server) {
		running = false;
		obtainableItems.clear();
		for (Item item : BuiltInRegistries.ITEM) {
			String id = BuiltInRegistries.ITEM.getKey(item).toString();
			if (item != Items.AIR && !ITEM_BLACKLIST.contains(id)) {
				obtainableItems.add(item);
			}
		}
		rebuildDropMap();
		force.resolveTargets();
		applyUhcGameRule(server);
		SplitManager.get().sync(server);
		ChallengesMod.LOGGER.info("Challenges geladen: {} Items verfügbar", obtainableItems.size());
	}

	public void onServerStopping() {
		running = false;
		save();
	}

	public void rebuildDropMap() {
		dropMap.clear();
		List<Item> shuffled = new ArrayList<>(obtainableItems);
		Collections.shuffle(shuffled, new Random(settings.dropSeed));
		for (int i = 0; i < obtainableItems.size(); i++) {
			dropMap.put(obtainableItems.get(i), shuffled.get(i));
		}
	}

	/** UHC = keine natürliche Regeneration. Wird per Gamerule in allen Dimensionen gesetzt. */
	public void applyUhcGameRule(MinecraftServer server) {
		String value = settings.uhc ? "false" : "true";
		for (ServerLevel level : server.getAllLevels()) {
			server.getCommands().performPrefixedCommand(
					server.createCommandSourceStack().withLevel(level),
					"gamerule minecraft:natural_health_regeneration " + value);
		}
	}

	// ------------------------------------------------------------------
	// Timer-Steuerung
	// ------------------------------------------------------------------

	public boolean start(MinecraftServer server) {
		if (settings.ended) {
			return false;
		}
		running = true;
		broadcast(server, Component.literal("Der Timer läuft! Viel Glück!").withStyle(ChatFormatting.GREEN));
		SplitManager.get().onTimerStart(server);
		return true;
	}

	public void pause(MinecraftServer server) {
		running = false;
		save();
		broadcast(server, Component.literal("Der Timer wurde pausiert.").withStyle(ChatFormatting.YELLOW));
	}

	/** Setzt Timer und Status zurück, ohne die Welt zu löschen. */
	public void resetTimer(MinecraftServer server) {
		running = false;
		settings.timerTicks = 0;
		settings.ended = false;
		force.reset();
		SplitManager.get().resetRun(server);
		save();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				player.setGameMode(GameType.SURVIVAL);
			}
		}
		broadcast(server, Component.literal("Timer zurückgesetzt.").withStyle(ChatFormatting.YELLOW));
	}

	// ------------------------------------------------------------------
	// Ende der Challenge
	// ------------------------------------------------------------------

	public void win(MinecraftServer server, String reason) {
		if (settings.ended) {
			return;
		}
		running = false;
		settings.ended = true;
		force.reset();
		SplitManager.get().resetRun(server);
		save();
		showTitle(server,
				Component.literal("Challenge geschafft!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.literal(reason + " – Zeit: " + formatTime(settings.timerTicks)).withStyle(ChatFormatting.YELLOW));
		broadcast(server, Component.literal("Challenge geschafft! " + reason + " – Zeit: " + formatTime(settings.timerTicks))
				.withStyle(ChatFormatting.GOLD));
	}

	public void fail(MinecraftServer server, String reason) {
		if (settings.ended) {
			return;
		}
		running = false;
		settings.ended = true;
		force.reset();
		SplitManager.get().resetRun(server);
		save();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.setGameMode(GameType.SPECTATOR);
		}
		showTitle(server,
				Component.literal("Challenge fehlgeschlagen").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
				Component.literal(reason).withStyle(ChatFormatting.GRAY));
		broadcast(server, Component.literal("Challenge fehlgeschlagen: " + reason + " – Zeit: " + formatTime(settings.timerTicks))
				.withStyle(ChatFormatting.RED));
		broadcast(server, Component.literal("Mit /challenge reset gibt es eine neue Welt, mit /timer reset geht es in dieser Welt weiter.")
				.withStyle(ChatFormatting.GRAY));
	}

	// ------------------------------------------------------------------
	// Events
	// ------------------------------------------------------------------

	public void onEntityDeath(LivingEntity entity, DamageSource source) {
		MinecraftServer server = entity.level().getServer();
		if (server == null || !running) {
			return;
		}
		if (entity instanceof ServerPlayer player) {
			if (settings.splitMode) {
				// Split-Challenge: Stirbt einer, sterben alle
				if (SplitManager.get().onPlayerDeath(server, player)) {
					return;
				}
				fail(server, player.getName().getString() + " ist gestorben – und alle mit ihm");
				return;
			}
			if (settings.deathEndsChallenge) {
				fail(server, player.getName().getString() + " ist gestorben");
			}
			return;
		}
		if (settings.dragonGoal && "minecraft:ender_dragon".equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())) {
			win(server, "Der Enderdrache ist besiegt");
			return;
		}
		if (source.getEntity() instanceof ServerPlayer killer) {
			force.onMobKilled(server, this, killer, entity.getType());
		}
	}

	/** Pause-Schutz: Solange der Timer nicht läuft, bekommen Spieler keinen Schaden. */
	public boolean allowPlayerDamage(LivingEntity entity) {
		if (!(entity instanceof ServerPlayer)) {
			return true;
		}
		return running || !settings.pauseProtection || settings.ended;
	}

	/** Pause-Schutz: Solange der Timer nicht läuft, können keine Blöcke abgebaut werden. */
	public boolean allowBlockBreak(ServerPlayer player) {
		if (running || !settings.pauseProtection || player.isCreative()) {
			return true;
		}
		player.connection.send(new ClientboundSetActionBarTextPacket(
				Component.literal("Starte zuerst den Timer mit /timer start").withStyle(ChatFormatting.RED)));
		return false;
	}

	public void onPlayerReady(ServerPlayer player) {
		if (settings.ended) {
			player.setGameMode(GameType.SPECTATOR);
		}
	}

	/** Zufalls-Drops für Blöcke und Mobs. Jedes Item wird immer auf dasselbe Zufalls-Item abgebildet. */
	public void modifyDrops(Holder<LootTable> holder, List<ItemStack> drops) {
		if (!settings.randomBlockDrops && !settings.randomMobDrops) {
			return;
		}
		Optional<ResourceKey<LootTable>> key = holder.unwrapKey();
		if (key.isEmpty()) {
			return;
		}
		String path = key.get().identifier().getPath();
		boolean isBlock = path.startsWith("blocks/");
		boolean isMob = path.startsWith("entities/");
		if (!(isBlock && settings.randomBlockDrops) && !(isMob && settings.randomMobDrops)) {
			return;
		}
		for (int i = 0; i < drops.size(); i++) {
			ItemStack original = drops.get(i);
			if (original.isEmpty()) {
				continue;
			}
			Item replacement = dropMap.get(original.getItem());
			if (replacement == null) {
				continue;
			}
			ItemStack stack = new ItemStack(replacement);
			stack.setCount(Math.max(1, Math.min(original.getCount(), stack.getMaxStackSize())));
			drops.set(i, stack);
		}
	}

	// ------------------------------------------------------------------
	// Tick
	// ------------------------------------------------------------------

	public void tick(MinecraftServer server) {
		tickCounter++;

		if (running) {
			settings.timerTicks++;

			if (settings.sharedHearts) {
				syncSharedHealth(server);
			} else {
				sharedHealth = Float.NaN;
			}

			if (settings.jumpDamage) {
				tickJumpDamage(server);
			} else {
				lastJumps.clear();
			}

			if (settings.sneakDeath) {
				tickSneakDeath(server);
			}

			if (settings.randomItem && settings.timerTicks % (settings.randomItemSeconds * 20L) == 0) {
				giveRandomItems(server);
			}

			force.tick(server, this);
			SplitManager.get().tick(server);
		} else {
			sharedHealth = Float.NaN;
			lastJumps.clear();
		}

		if (tickCounter % 10 == 0) {
			sendActionBar(server);
			updateRandomItemBar(server);
			force.updateBossBar(server, this);
		}
		if (tickCounter % (20 * 60) == 0) {
			save();
		}
	}

	public List<ServerPlayer> activePlayers(MinecraftServer server) {
		List<ServerPlayer> result = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isAlive() && !player.isSpectator() && !player.isCreative()) {
				result.add(player);
			}
		}
		return result;
	}

	private void syncSharedHealth(MinecraftServer server) {
		List<ServerPlayer> players = activePlayers(server);
		if (players.isEmpty()) {
			return;
		}
		if (Float.isNaN(sharedHealth)) {
			sharedHealth = players.get(0).getHealth();
		}
		float target = sharedHealth;
		float biggestChange = 0;
		for (ServerPlayer player : players) {
			float change = Math.abs(player.getHealth() - sharedHealth);
			if (change > biggestChange) {
				biggestChange = change;
				target = player.getHealth();
			}
		}
		if (biggestChange < 0.01f) {
			return;
		}
		sharedHealth = target;
		if (target <= 0) {
			return;
		}
		for (ServerPlayer player : players) {
			if (Math.abs(player.getHealth() - target) > 0.01f) {
				player.setHealth(target);
			}
		}
	}

	private void tickJumpDamage(MinecraftServer server) {
		for (ServerPlayer player : activePlayers(server)) {
			int jumps = player.getStats().getValue(Stats.CUSTOM, Stats.JUMP);
			Integer before = lastJumps.put(player.getUUID(), jumps);
			if (before != null && jumps > before) {
				ServerLevel level = player.level();
				player.hurtServer(level, level.damageSources().generic(), settings.jumpDamageHearts * 2.0f);
			}
		}
	}

	private void tickSneakDeath(MinecraftServer server) {
		for (ServerPlayer player : activePlayers(server)) {
			if (player.isShiftKeyDown()) {
				broadcast(server, Component.literal(player.getName().getString() + " hat geschlichen!").withStyle(ChatFormatting.RED));
				player.kill(player.level());
			}
		}
	}

	private void giveRandomItems(MinecraftServer server) {
		if (obtainableItems.isEmpty()) {
			return;
		}
		for (ServerPlayer player : activePlayers(server)) {
			Item item = obtainableItems.get(random.nextInt(obtainableItems.size()));
			ItemStack stack = new ItemStack(item);
			Component name = stack.getHoverName();
			player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
			player.sendSystemMessage(Component.literal("Zufalls-Item: ").withStyle(ChatFormatting.AQUA)
					.append(name.copy().withStyle(ChatFormatting.WHITE)), false);
		}
	}

	// ------------------------------------------------------------------
	// Anzeige
	// ------------------------------------------------------------------

	private void sendActionBar(MinecraftServer server) {
		MutableComponent text;
		if (settings.ended) {
			text = Component.literal("Challenge beendet – " + formatTime(settings.timerTicks)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
		} else if (running) {
			text = Component.literal(formatTime(settings.timerTicks)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		} else {
			text = Component.literal("Timer pausiert").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC);
		}
		ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(text);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.connection.send(packet);
		}
	}

	/** Leiste oben: Countdown bis zum nächsten Zufalls-Item. */
	private void updateRandomItemBar(MinecraftServer server) {
		if (!settings.randomItem || !running || settings.ended) {
			BossBars.hide(server, "random_item");
			return;
		}
		int interval = settings.randomItemSeconds;
		long intervalTicks = interval * 20L;
		int secondsLeft = (int) Math.ceil((intervalTicks - (settings.timerTicks % intervalTicks)) / 20.0);
		Component name = Component.literal("Nächstes Zufalls-Item in ").withStyle(ChatFormatting.AQUA)
				.append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
		BossBars.show(server, "random_item", name, secondsLeft, interval, BossEvent.BossBarColor.BLUE);
	}

	public void showTitle(MinecraftServer server, Component title, Component subtitle) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
			if (subtitle != null) {
				player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
			}
			player.connection.send(new ClientboundSetTitleTextPacket(title));
		}
	}

	public static void broadcast(MinecraftServer server, Component message) {
		Component full = Component.literal("[Challenges] ").withStyle(ChatFormatting.DARK_AQUA).append(message);
		server.getPlayerList().broadcastSystemMessage(full, false);
	}

	public static String formatTime(long ticks) {
		long totalSeconds = ticks / 20;
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		return String.format("%02d:%02d:%02d", hours, minutes, seconds);
	}

	public Random random() {
		return random;
	}
}
