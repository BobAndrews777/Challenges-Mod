package de.bob.challenges.split;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

import de.bob.challenges.ChallengeManager;
import de.bob.challenges.ChallengeSettings;

/**
 * Die Split-Challenge: Farben, Zeitfenster, Countdown, Joker und die einzelnen Splits.
 */
public final class SplitManager {
	private static final SplitManager INSTANCE = new SplitManager();

	private int countdownTicks = -1;
	private SplitType pending = null;
	private boolean killingAll = false;
	private long tickCounter = 0;

	private SplitManager() {
	}

	public static SplitManager get() {
		return INSTANCE;
	}

	private ChallengeManager manager() {
		return ChallengeManager.get();
	}

	private ChallengeSettings s() {
		return manager().settings();
	}

	public boolean isActive(SplitType type) {
		return s().splitMode && s().activeSplits.contains(type.name());
	}

	// ------------------------------------------------------------------
	// Ablauf
	// ------------------------------------------------------------------

	/** Wird aufgerufen, wenn der Timer startet. */
	public void onTimerStart(MinecraftServer server) {
		if (!s().splitMode) {
			sync(server);
			return;
		}
		assignColors(server, false);
		if (s().nextSplitAt < s().timerTicks) {
			scheduleNext();
		}
		manager().save();
		sync(server);
	}

	/** Setzt den Run zurück: keine aktiven Splits, Joker wieder voll. Farben bleiben. */
	public void resetRun(MinecraftServer server) {
		ChallengeSettings s = s();
		s.activeSplits.clear();
		s.removedSplits.clear();
		s.weaponOwners.clear();
		s.toolOwners.clear();
		s.jokersLeft = s.splitJokers;
		s.nextSplitAt = -1;
		s.placeTurn = 0;
		countdownTicks = -1;
		pending = null;
		if (server != null) {
			sync(server);
		} else {
			applyFlags();
		}
	}

	private void scheduleNext() {
		ChallengeSettings s = s();
		long min = s.splitMinMinutes * 60L * 20L;
		long max = s.splitMaxMinutes * 60L * 20L;
		long delay = min + (max > min ? (long) (manager().random().nextDouble() * (max - min + 1)) : 0);
		s.nextSplitAt = s.timerTicks + delay;
	}

	/** Wird jeden Tick aufgerufen, solange der Timer läuft. */
	public void tick(MinecraftServer server) {
		tickCounter++;
		ChallengeSettings s = s();
		if (!s.splitMode) {
			return;
		}

		if (countdownTicks >= 0) {
			if (countdownTicks % 20 == 0 && countdownTicks > 0) {
				int seconds = countdownTicks / 20;
				titleAll(server, Component.literal(String.valueOf(seconds)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
						Component.literal("Ein neuer Split kommt …").withStyle(ChatFormatting.GRAY), 0, 22, 0);
				sound(server, "minecraft:block.note_block.hat", 1.0f);
			}
			if (countdownTicks == 0) {
				SplitType type = pending;
				pending = null;
				countdownTicks = -1;
				if (type != null) {
					activate(server, type);
				}
			} else {
				countdownTicks--;
			}
		} else if (s.nextSplitAt >= 0 && s.timerTicks >= s.nextSplitAt) {
			SplitType next = pickRandom();
			if (next == null) {
				s.nextSplitAt = -1; // alle Splits sind schon dran
			} else {
				pending = next;
				countdownTicks = 5 * 20;
			}
		}

		if (tickCounter % 5 == 0) {
			if (isActive(SplitType.WEAPONS) || isActive(SplitType.TOOLS)) {
				for (ServerPlayer player : coloredPlayers(server)) {
					checkClaims(server, player);
				}
			}
		}
		if (tickCounter % 20 == 0 && isActive(SplitType.STACKS)) {
			for (ServerPlayer player : coloredPlayers(server)) {
				trimStacks(player);
			}
		}
	}

	private SplitType pickRandom() {
		List<SplitType> pool = new ArrayList<>();
		for (SplitType type : SplitType.values()) {
			if (type.implemented && !s().activeSplits.contains(type.name()) && !s().removedSplits.contains(type.name())) {
				pool.add(type);
			}
		}
		return pool.isEmpty() ? null : pool.get(manager().random().nextInt(pool.size()));
	}

	public void activate(MinecraftServer server, SplitType type) {
		ChallengeSettings s = s();
		if (!s.activeSplits.contains(type.name())) {
			s.activeSplits.add(type.name());
		}
		s.removedSplits.remove(type.name());

		titleAll(server, Component.literal("SPLIT").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
				Component.literal(type.announcement).withStyle(ChatFormatting.GREEN), 5, 60, 15);
		sound(server, "minecraft:entity.player.levelup", 1.0f);

		MutableComponent message = Component.literal(type.announcement + " ").withStyle(ChatFormatting.GREEN);
		if (s.jokersLeft > 0) {
			message.append(Component.literal("[Joker einsetzen]").withStyle(style -> style
					.withColor(ChatFormatting.GOLD)
					.withBold(true)
					.withClickEvent(new ClickEvent.RunCommand("/joker " + type.commandName()))
					.withHoverEvent(new HoverEvent.ShowText(Component.literal("Diesen Split für immer abschalten (noch " + s.jokersLeft + " Joker)")))));
		}
		ChallengeManager.broadcast(server, message);

		if (type == SplitType.STACKS) {
			SplitFlags.stacksActive = true;
			for (ServerPlayer player : coloredPlayers(server)) {
				trimStacks(player);
			}
		}
		if (type == SplitType.PLACING) {
			s.placeTurn = 0;
		}

		scheduleNext();
		manager().save();
		sync(server);
	}

	/** Joker: aktiven Split für immer abschalten. */
	public boolean useJoker(MinecraftServer server, SplitType type) {
		ChallengeSettings s = s();
		if (s.jokersLeft <= 0 || !s.activeSplits.remove(type.name())) {
			return false;
		}
		if (!s.removedSplits.contains(type.name())) {
			s.removedSplits.add(type.name());
		}
		s.jokersLeft--;
		if (type == SplitType.WEAPONS) {
			s.weaponOwners.clear();
		}
		if (type == SplitType.TOOLS) {
			s.toolOwners.clear();
		}
		ChallengeManager.broadcast(server, Component.literal("Joker eingesetzt: " + type.displayName + " ist nicht mehr geteilt. Übrige Joker: " + s.jokersLeft)
				.withStyle(ChatFormatting.GOLD));
		sound(server, "minecraft:block.beacon.deactivate", 1.0f);
		manager().save();
		sync(server);
		return true;
	}

	// ------------------------------------------------------------------
	// Farben
	// ------------------------------------------------------------------

	/** Verteilt die Farben an bis zu 4 Spieler. Mit force=true werden alle neu verteilt. */
	public void assignColors(MinecraftServer server, boolean force) {
		ChallengeSettings s = s();
		if (force) {
			s.playerColors.clear();
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			assignColor(server, player);
		}
		setupTeams(server);
	}

	private void assignColor(MinecraftServer server, ServerPlayer player) {
		ChallengeSettings s = s();
		String uuid = player.getUUID().toString();
		s.playerNames.put(uuid, player.getName().getString());
		if (s.playerColors.containsKey(uuid)) {
			return;
		}
		for (SplitColor color : SplitColor.values()) {
			if (!s.playerColors.containsValue(color.name())) {
				s.playerColors.put(uuid, color.name());
				ChallengeManager.broadcast(server, Component.literal(player.getName().getString() + " ist ").withStyle(ChatFormatting.WHITE)
						.append(Component.literal(color.germanName).withStyle(color.chat, ChatFormatting.BOLD)));
				return;
			}
		}
		player.sendSystemMessage(Component.literal("Es gibt nur 4 Farben – du spielst ohne Farbe mit.").withStyle(ChatFormatting.GRAY), false);
	}

	private void setupTeams(MinecraftServer server) {
		for (SplitColor color : SplitColor.values()) {
			command(server, "team add " + color.teamName());
			command(server, "team modify " + color.teamName() + " color " + color.teamColor);
		}
		for (Map.Entry<String, String> entry : s().playerColors.entrySet()) {
			String name = s().playerNames.get(entry.getKey());
			SplitColor color = colorOf(entry.getValue());
			if (name != null && color != null) {
				command(server, "team join " + color.teamName() + " " + name);
			}
		}
	}

	private static SplitColor colorOf(String name) {
		try {
			return SplitColor.valueOf(name);
		} catch (IllegalArgumentException | NullPointerException e) {
			return null;
		}
	}

	public SplitColor colorOf(Player player) {
		return colorOf(s().playerColors.get(player.getUUID().toString()));
	}

	/** Online-Spieler mit Farbe, sortiert nach Farbe (Rot, Gelb, Blau, Grün). */
	public List<ServerPlayer> coloredPlayers(MinecraftServer server) {
		List<ServerPlayer> result = new ArrayList<>();
		for (SplitColor color : SplitColor.values()) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (color == colorOf(player) && !player.isSpectator()) {
					result.add(player);
				}
			}
		}
		return result;
	}

	public void onJoin(MinecraftServer server, ServerPlayer player) {
		if (s().splitMode && manager().isRunning() && !player.isSpectator()) {
			assignColor(server, player);
			setupTeams(server);
		}
		s().playerNames.put(player.getUUID().toString(), player.getName().getString());
		sync(server);
	}

	// ------------------------------------------------------------------
	// Tod: einer stirbt → alle sterben
	// ------------------------------------------------------------------

	/** @return true, wenn der Tod schon behandelt wird (Folgetode beim „alle sterben“). */
	public boolean onPlayerDeath(MinecraftServer server, ServerPlayer dead) {
		if (killingAll) {
			return true;
		}
		killingAll = true;
		try {
			for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
				if (player != dead && player.isAlive() && !player.isSpectator()) {
					player.kill(player.level());
				}
			}
		} finally {
			killingAll = false;
		}
		return false;
	}

	// ------------------------------------------------------------------
	// Split 13: Betten
	// ------------------------------------------------------------------

	/** Betten werden nie als belegt markiert, damit alle in dasselbe Bett passen. */
	public boolean skipBedOccupation(Entity entity) {
		return isActive(SplitType.BEDS) && entity instanceof Player;
	}

	/** @return Fehlermeldung, wenn schon jemand in einem anderen Bett liegt, sonst null. */
	public Component checkSameBed(Player sleeper, BlockPos bedPos) {
		if (!isActive(SplitType.BEDS) || !(sleeper instanceof ServerPlayer serverPlayer)) {
			return null;
		}
		MinecraftServer server = serverPlayer.level().getServer();
		if (server == null) {
			return null;
		}
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (other == sleeper || !other.isSleeping()) {
				continue;
			}
			BlockPos otherBed = other.getSleepingPos().orElse(null);
			if (otherBed != null && !otherBed.equals(bedPos)) {
				return Component.literal("Ihr müsst alle im selben Bett schlafen! " + other.getName().getString() + " liegt schon woanders.");
			}
		}
		return null;
	}

	// ------------------------------------------------------------------
	// Split 14: Platzieren
	// ------------------------------------------------------------------

	private String currentPlaceTurnName(MinecraftServer server) {
		if (!isActive(SplitType.PLACING)) {
			return "";
		}
		List<ServerPlayer> players = coloredPlayers(server);
		if (players.isEmpty()) {
			return "";
		}
		return players.get(Math.floorMod(s().placeTurn, players.size())).getName().getString();
	}

	public void onBlockPlaced(ServerPlayer player) {
		if (!isActive(SplitType.PLACING)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null || !player.getName().getString().equals(currentPlaceTurnName(server))) {
			return;
		}
		s().placeTurn++;
		sync(server);
		String next = currentPlaceTurnName(server);
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (other.getName().getString().equals(next)) {
				other.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("Du darfst jetzt einen Block platzieren!").withStyle(ChatFormatting.GREEN)));
			}
		}
	}

	public void notifyNotYourTurn(ServerPlayer player) {
		player.connection.send(new ClientboundSetActionBarTextPacket(
				Component.literal("Nicht dein Zug! Platzieren darf gerade: " + SplitFlags.placeTurnName).withStyle(ChatFormatting.RED)));
	}

	// ------------------------------------------------------------------
	// Split 17 + 23: Waffen und Werkzeuge
	// ------------------------------------------------------------------

	public static String weaponType(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (path.endsWith("_sword")) return "Schwert";
		if (path.endsWith("_axe")) return "Axt";
		if (path.endsWith("_spear") || path.equals("spear")) return "Speer";
		if (path.equals("mace")) return "Streitkolben";
		if (path.equals("trident")) return "Dreizack";
		if (path.equals("crossbow")) return "Armbrust";
		if (path.equals("bow")) return "Bogen";
		return null;
	}

	public static String toolType(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (path.endsWith("_pickaxe")) return "Spitzhacke";
		if (path.endsWith("_shovel")) return "Schaufel";
		if (path.endsWith("_hoe")) return "Hacke";
		if (path.endsWith("_axe")) return "Axt";
		return null;
	}

	/** Wer zuerst eine freie Waffen-/Werkzeugart in die Hand nimmt, dem gehört sie. */
	private void checkClaims(MinecraftServer server, ServerPlayer player) {
		ItemStack hand = player.getMainHandItem();
		if (isActive(SplitType.WEAPONS)) {
			claim(server, player, weaponType(hand), s().weaponOwners, "Waffe");
		}
		if (isActive(SplitType.TOOLS)) {
			claim(server, player, toolType(hand), s().toolOwners, "Werkzeug");
		}
	}

	private void claim(MinecraftServer server, ServerPlayer player, String type, Map<String, String> owners, String what) {
		if (type == null) {
			return;
		}
		String uuid = player.getUUID().toString();
		if (owners.containsKey(type) || owners.containsValue(uuid) || colorOf(player) == null) {
			return;
		}
		owners.put(type, uuid);
		SplitColor color = colorOf(player);
		ChallengeManager.broadcast(server, Component.literal(what + " " + type + " gehört jetzt ").withStyle(ChatFormatting.WHITE)
				.append(Component.literal(player.getName().getString()).withStyle(color.chat, ChatFormatting.BOLD)));
		manager().save();
	}

	/** null = erlaubt, sonst der Grund, warum nicht. */
	private String denyReason(Player player, String type, Map<String, String> owners, String what) {
		if (type == null) {
			return null;
		}
		String uuid = player.getUUID().toString();
		String owner = owners.get(type);
		if (owner == null) {
			// Noch frei: erlaubt, wenn man selbst noch keine Art besitzt
			if (owners.containsValue(uuid)) {
				return "Du hast schon ein(e) andere(s) " + what + " – " + type + " darfst du nicht benutzen.";
			}
			return null;
		}
		if (owner.equals(uuid)) {
			return null;
		}
		String ownerName = s().playerNames.getOrDefault(owner, "jemand anderem");
		return type + " gehört " + ownerName + "!";
	}

	public InteractionResult checkWeaponUse(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer) || !isActive(SplitType.WEAPONS)) {
			return InteractionResult.PASS;
		}
		String reason = denyReason(player, weaponType(player.getMainHandItem()), s().weaponOwners, "Waffe");
		if (reason == null) {
			return InteractionResult.PASS;
		}
		serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(reason).withStyle(ChatFormatting.RED)));
		return InteractionResult.FAIL;
	}

	public InteractionResult checkToolUse(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer) || !isActive(SplitType.TOOLS)) {
			return InteractionResult.PASS;
		}
		String reason = denyReason(player, toolType(player.getMainHandItem()), s().toolOwners, "Werkzeug");
		if (reason == null) {
			return InteractionResult.PASS;
		}
		serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(reason).withStyle(ChatFormatting.RED)));
		return InteractionResult.FAIL;
	}

	// ------------------------------------------------------------------
	// Split 22: Stacks
	// ------------------------------------------------------------------

	/** Zu große Stapel (aus der Zeit vor dem Split) werden auf 16 aufgeteilt. */
	private void trimStacks(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		List<ItemStack> overflow = new ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			int max = stack.getMaxStackSize();
			if (!stack.isEmpty() && stack.getCount() > max) {
				overflow.add(stack.split(stack.getCount() - max));
			}
		}
		for (ItemStack extra : overflow) {
			player.getInventory().placeItemBackInInventory(extra, Prediction.SERVER_ONLY);
		}
	}

	// ------------------------------------------------------------------
	// Split 24: Enderperlen
	// ------------------------------------------------------------------

	/** Wird aufgerufen, wenn eine Entity verschwindet. Bei einer gelandeten Enderperle kommen alle mit. */
	public void onEntityRemoved(Entity entity, ServerLevel level) {
		if (!isActive(SplitType.ENDER_PEARLS) || !(entity instanceof Projectile projectile)) {
			return;
		}
		if (!"minecraft:ender_pearl".equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString())) {
			return;
		}
		if (!(projectile.getOwner() instanceof ServerPlayer owner) || owner.level() != level || !owner.isAlive()) {
			return;
		}
		// Der Werfer wurde schon teleportiert – er steht jetzt da, wo die Perle gelandet ist.
		if (owner.distanceToSqr(entity) > 9.0) {
			return;
		}
		for (ServerPlayer other : level.getServer().getPlayerList().getPlayers()) {
			if (other != owner && other.isAlive() && !other.isSpectator()) {
				other.teleportTo(level, owner.getX(), owner.getY(), owner.getZ(), Set.of(), other.getYRot(), other.getXRot(), true);
			}
		}
	}

	// ------------------------------------------------------------------
	// Sync zum Client
	// ------------------------------------------------------------------

	private void applyFlags() {
		SplitFlags.stacksActive = isActive(SplitType.STACKS);
		SplitFlags.placingActive = isActive(SplitType.PLACING);
	}

	public void sync(MinecraftServer server) {
		applyFlags();
		SplitFlags.placeTurnName = currentPlaceTurnName(server);

		List<SplitSyncPayload.Entry> entries = new ArrayList<>();
		if (s().splitMode) {
			for (SplitColor color : SplitColor.values()) {
				for (Map.Entry<String, String> entry : s().playerColors.entrySet()) {
					if (color.name().equals(entry.getValue())) {
						String name = s().playerNames.getOrDefault(entry.getKey(), "?");
						entries.add(new SplitSyncPayload.Entry(name, color.ordinal()));
					}
				}
			}
		}
		List<String> active = new ArrayList<>();
		if (s().splitMode) {
			for (String name : s().activeSplits) {
				try {
					active.add(SplitType.valueOf(name).displayName);
				} catch (IllegalArgumentException ignored) {
				}
			}
		}
		SplitSyncPayload payload = new SplitSyncPayload(entries, active, SplitFlags.placeTurnName, SplitFlags.stacksActive);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, SplitSyncPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	// ------------------------------------------------------------------
	// Hilfen
	// ------------------------------------------------------------------

	private static void titleAll(MinecraftServer server, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
			player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
			player.connection.send(new ClientboundSetTitleTextPacket(title));
		}
	}

	private static void sound(MinecraftServer server, String sound, float pitch) {
		command(server, "execute as @a at @s run playsound " + sound + " master @s ~ ~ ~ 1 " + pitch);
	}

	private static void command(MinecraftServer server, String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
	}

	public String statusText() {
		ChallengeSettings s = s();
		StringBuilder sb = new StringBuilder();
		sb.append("Split-Challenge: ").append(s.splitMode ? "AN" : "AUS");
		sb.append(" | Zeitfenster: ").append(s.splitMinMinutes).append("–").append(s.splitMaxMinutes).append(" Min");
		sb.append(" | Joker: ").append(s.jokersLeft).append("/").append(s.splitJokers);
		List<String> active = new ArrayList<>();
		for (String name : s.activeSplits) {
			SplitType type = SplitType.byCommandName(name);
			active.add(type != null ? type.displayName : name);
		}
		sb.append("\nAktive Splits: ").append(active.isEmpty() ? "keine" : String.join(", ", active));
		return sb.toString();
	}
}
