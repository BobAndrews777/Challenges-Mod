package de.bob.challenges;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

/**
 * Force Battle: Jeder Spieler bekommt seine eigene Aufgabe (Item, Mob, Block, Biom, Fortschritt – abwechselnd).
 * Geschafft = Punkt + nächste Aufgabe. 2 Joker zum Überspringen (zählen auch als Punkt).
 * Nach Ablauf der Zeit gewinnt, wer die meisten Punkte hat.
 */
public final class ForceBattle {
	public enum Kind {
		ITEM("Item", "Besorge"), MOB("Mob", "Töte"), BLOCK("Block", "Stell dich auf"), BIOME("Biom", "Finde"), ADVANCEMENT("Fortschritt", "Erreiche");

		final String title;
		final String verb;

		Kind(String title, String verb) {
			this.title = title;
			this.verb = verb;
		}
	}

	private static final ForceBattle INSTANCE = new ForceBattle();
	private static final String JOKER_MARK = "challenges_force_joker";

	private final Random random = new Random();
	private final Map<UUID, Task> tasks = new HashMap<>();
	private final Map<UUID, Integer> points = new HashMap<>();
	private final Map<UUID, Integer> rotation = new HashMap<>();
	private final Map<UUID, String> names = new HashMap<>();
	private boolean started;
	private long ticksLeft;
	private long tick;

	private record Task(Kind kind, Object target, Component name) {
	}

	public static ForceBattle get() {
		return INSTANCE;
	}

	private static ChallengeSettings s() {
		return ChallengeManager.get().settings();
	}

	public boolean enabled() {
		return s().forceBattle && !kinds().isEmpty();
	}

	List<Kind> kinds() {
		List<Kind> list = new ArrayList<>();
		if (s().battleItem) list.add(Kind.ITEM);
		if (s().battleMob) list.add(Kind.MOB);
		if (s().battleBlock) list.add(Kind.BLOCK);
		if (s().battleBiome) list.add(Kind.BIOME);
		if (s().battleAdvancement) list.add(Kind.ADVANCEMENT);
		return list;
	}

	public void reset(MinecraftServer server) {
		started = false;
		tasks.clear();
		points.clear();
		rotation.clear();
		names.clear();
		ticksLeft = 0;
		if (server != null) {
			hideAll(server);
		}
	}

	// ------------------------------------------------------------------

	public void tick(MinecraftServer server) {
		ChallengeManager manager = ChallengeManager.get();
		if (!enabled()) {
			if (started) {
				reset(server);
			}
			return;
		}
		if (!manager.isRunning() || manager.isEnded()) {
			return;
		}
		tick++;
		if (!started) {
			started = true;
			ticksLeft = s().battleMinutes * 60L * 20L;
			ChallengeManager.broadcast(server, Component.literal("Force Battle! " + s().battleMinutes
					+ " Minuten – wer die meisten Aufgaben schafft, gewinnt.").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
		}
		ticksLeft--;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			names.put(player.getUUID(), player.getName().getString());
			if (!tasks.containsKey(player.getUUID())) {
				points.putIfAbsent(player.getUUID(), 0);
				giveJokers(player);
				nextTask(player);
			}
			if (tick % 5 == 0 && player.isAlive() && isDone(player, tasks.get(player.getUUID()))) {
				score(player, false);
			}
		}
		if (tick % 20 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				updateBar(server, player);
			}
		}
		if (ticksLeft <= 0) {
			finish(server);
		}
	}

	private void nextTask(ServerPlayer player) {
		List<Kind> kinds = kinds();
		int index = rotation.merge(player.getUUID(), 1, Integer::sum) - 1;
		for (int attempt = 0; attempt < kinds.size(); attempt++) {
			Kind kind = kinds.get((index + attempt) % kinds.size());
			Task task = createTask(player, kind);
			if (task != null) {
				tasks.put(player.getUUID(), task);
				player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("Neue Aufgabe – " + kind.title + ": ").withStyle(ChatFormatting.LIGHT_PURPLE)
						.append(task.name().copy().withStyle(ChatFormatting.AQUA))));
				return;
			}
		}
	}

	private Task createTask(ServerPlayer player, Kind kind) {
		ForceChallenge force = ChallengeManager.get().force();
		switch (kind) {
			case ITEM -> {
				List<Item> items = force.itemTargets();
				if (items.isEmpty()) return null;
				Item item = items.get(random.nextInt(items.size()));
				return new Task(kind, item, new ItemStack(item).getHoverName());
			}
			case MOB -> {
				List<EntityType<?>> mobs = force.mobTargets();
				if (mobs.isEmpty()) return null;
				EntityType<?> mob = mobs.get(random.nextInt(mobs.size()));
				return new Task(kind, mob, mob.getDescription());
			}
			case BLOCK -> {
				List<Block> blocks = force.blockTargets();
				if (blocks.isEmpty()) return null;
				Block block = blocks.get(random.nextInt(blocks.size()));
				return new Task(kind, block, block.getName());
			}
			case BIOME -> {
				List<ResourceKey<Biome>> biomes = new ArrayList<>();
				player.level().getServer().registryAccess().lookupOrThrow(Registries.BIOME).listElementIds()
						.filter(key -> key.identifier().getNamespace().equals("minecraft") && !key.identifier().getPath().equals("the_void"))
						.forEach(biomes::add);
				if (biomes.isEmpty()) return null;
				ResourceKey<Biome> biome = biomes.get(random.nextInt(biomes.size()));
				return new Task(kind, biome, Component.translatable("biome.minecraft." + biome.identifier().getPath()));
			}
			case ADVANCEMENT -> {
				List<AdvancementHolder> list = new ArrayList<>();
				for (AdvancementHolder holder : player.level().getServer().getAdvancements().getAllAdvancements()) {
					String path = holder.id().getPath();
					if (!holder.id().getNamespace().equals("minecraft") || path.startsWith("recipes/") || path.endsWith("/root")) {
						continue;
					}
					if (holder.value().display().isEmpty() || holder.value().display().get().hidden()) {
						continue;
					}
					if (player.getAdvancements().getOrStartProgress(holder).isDone()) {
						continue;
					}
					list.add(holder);
				}
				if (list.isEmpty()) return null;
				AdvancementHolder holder = list.get(random.nextInt(list.size()));
				return new Task(kind, holder, holder.value().display().get().title());
			}
		}
		return null;
	}

	private boolean isDone(ServerPlayer player, Task task) {
		if (task == null) {
			return false;
		}
		return switch (task.kind()) {
			case ITEM -> player.getInventory().countItem((Item) task.target()) > 0;
			case BLOCK -> player.level().getBlockState(player.blockPosition()).is((Block) task.target())
					|| player.level().getBlockState(player.blockPosition().below()).is((Block) task.target());
			case BIOME -> player.level().getBiome(player.blockPosition()).is((ResourceKey<Biome>) task.target());
			case ADVANCEMENT -> player.getAdvancements().getOrStartProgress((AdvancementHolder) task.target()).isDone();
			case MOB -> false;   // über onMobKilled
		};
	}

	public void onMobKilled(ServerPlayer killer, EntityType<?> type) {
		if (!started || !enabled()) {
			return;
		}
		Task task = tasks.get(killer.getUUID());
		if (task != null && task.kind() == Kind.MOB && task.target() == type) {
			score(killer, false);
		}
	}

	private void score(ServerPlayer player, boolean joker) {
		Task task = tasks.get(player.getUUID());
		int total = points.merge(player.getUUID(), 1, Integer::sum);
		if (task != null) {
			ChallengeManager.broadcast(player.level().getServer(), Component.literal(player.getName().getString() + (joker ? " überspringt " : " hat ")).withStyle(ChatFormatting.GRAY)
					.append(task.name().copy().withStyle(ChatFormatting.AQUA))
					.append(Component.literal(joker ? " mit einem Joker" : " geschafft").withStyle(ChatFormatting.GRAY))
					.append(Component.literal("  (" + total + " Punkte)").withStyle(ChatFormatting.GOLD)));
		}
		nextTask(player);
	}

	// ------------------------------------------------------------------
	// Joker
	// ------------------------------------------------------------------

	private void giveJokers(ServerPlayer player) {
		int count = s().battleJokers;
		if (count <= 0) {
			return;
		}
		ItemStack joker = new ItemStack(BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("barrier")), count);
		joker.set(DataComponents.CUSTOM_NAME, Component.literal("Joker (Rechtsklick: Aufgabe überspringen)").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(JOKER_MARK, true);
		joker.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		player.getInventory().placeItemBackInInventory(joker, net.minecraft.util.Prediction.SERVER_ONLY);
	}

	private static boolean isJoker(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(JOKER_MARK);
	}

	public InteractionResult onUseItem(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!isJoker(stack) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		if (started && tasks.containsKey(player.getUUID()) && ChallengeManager.get().isRunning()) {
			stack.shrink(1);
			score(serverPlayer, true);
		}
		return InteractionResult.SUCCESS;
	}

	// ------------------------------------------------------------------
	// Ende und Anzeige
	// ------------------------------------------------------------------

	private void finish(MinecraftServer server) {
		List<Map.Entry<UUID, Integer>> ranking = new ArrayList<>(points.entrySet());
		ranking.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		ChallengeManager.broadcast(server, Component.literal("Force Battle – Ergebnis:").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
		int place = 0;
		int lastPoints = -1;
		for (int i = 0; i < ranking.size(); i++) {
			Map.Entry<UUID, Integer> entry = ranking.get(i);
			if (entry.getValue() != lastPoints) {
				place = i + 1;
				lastPoints = entry.getValue();
			}
			ChallengeManager.broadcast(server, Component.literal(place + ". " + names.getOrDefault(entry.getKey(), "?") + " – " + entry.getValue() + " Punkte")
					.withStyle(place == 1 ? ChatFormatting.GOLD : ChatFormatting.WHITE));
		}
		String winner;
		if (ranking.isEmpty()) {
			winner = "Niemand";
		} else {
			int best = ranking.get(0).getValue();
			List<String> top = new ArrayList<>();
			for (Map.Entry<UUID, Integer> entry : ranking) {
				if (entry.getValue() == best) {
					top.add(names.getOrDefault(entry.getKey(), "?"));
				}
			}
			winner = String.join(" & ", top) + " mit " + best + " Punkten";
		}
		hideAll(server);
		started = false;
		tasks.clear();
		ChallengeManager.get().win(server, "Force Battle gewonnen: " + winner);
	}

	private void updateBar(MinecraftServer server, ServerPlayer player) {
		CustomBossEvents events = server.getCustomBossEvents();
		var id = ChallengesMod.id("battle_" + player.getUUID().toString().substring(0, 8));
		CustomBossEvent bar = events.get(id);
		Task task = tasks.get(player.getUUID());
		if (task == null) {
			if (bar != null) {
				bar.removeAllPlayers();
			}
			return;
		}
		int seconds = (int) Math.max(0, ticksLeft / 20);
		MutableComponent text = Component.literal(task.kind().verb + ": ").withStyle(ChatFormatting.LIGHT_PURPLE)
				.append(task.name().copy().withStyle(ChatFormatting.AQUA))
				.append(Component.literal("  ·  " + points.getOrDefault(player.getUUID(), 0) + " Punkte  ·  "
						+ String.format("%d:%02d", seconds / 60, seconds % 60)).withStyle(ChatFormatting.WHITE));
		if (bar == null) {
			bar = events.create(server.overworld().getRandom(), id, text);
		}
		bar.setName(text);
		bar.setColor(BossEvent.BossBarColor.PURPLE);
		bar.setMax(Math.max(1, s().battleMinutes * 60));
		bar.setValue(seconds);
		bar.setVisible(true);
		if (!bar.getPlayers().contains(player)) {
			bar.removeAllPlayers();
			bar.addPlayer(player);
		}
	}

	private void hideAll(MinecraftServer server) {
		CustomBossEvents events = server.getCustomBossEvents();
		for (UUID uuid : new ArrayList<>(names.keySet())) {
			CustomBossEvent bar = events.get(ChallengesMod.id("battle_" + uuid.toString().substring(0, 8)));
			if (bar != null) {
				bar.setVisible(false);
				bar.removeAllPlayers();
			}
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			CustomBossEvent bar = events.get(ChallengesMod.id("battle_" + player.getUUID().toString().substring(0, 8)));
			if (bar != null) {
				bar.setVisible(false);
				bar.removeAllPlayers();
			}
		}
	}
}
