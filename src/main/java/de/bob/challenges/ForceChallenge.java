package de.bob.challenges;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Force-Challenges: In regelmäßigen Abständen bekommt ihr eine Aufgabe
 * (auf einem Block stehen, einen Mob töten, ein Item besitzen) und müsst sie
 * vor Ablauf der Zeit schaffen. Ein Spieler reicht.
 */
public final class ForceChallenge {
	public enum Type {
		BLOCK("Force Block", "Stellt euch auf"),
		MOB("Force Mob", "Tötet"),
		ITEM("Force Item", "Besorgt");

		final String title;
		final String verb;

		Type(String title, String verb) {
			this.title = title;
			this.verb = verb;
		}
	}

	private static final String[] BLOCK_IDS = {
			"stone", "granite", "diorite", "andesite", "deepslate", "tuff", "calcite", "sand", "red_sand",
			"gravel", "clay", "sandstone", "oak_log", "birch_log", "spruce_log", "jungle_log", "acacia_log",
			"dark_oak_log", "oak_planks", "crafting_table", "furnace", "chest", "bookshelf", "coal_ore",
			"iron_ore", "copper_ore", "gold_ore", "redstone_ore", "lapis_ore", "diamond_ore",
			"deepslate_iron_ore", "obsidian", "netherrack", "magma_block", "soul_sand", "pumpkin", "melon",
			"hay_block", "white_wool", "bricks", "mossy_cobblestone", "snow_block", "ice", "glass",
			"smooth_stone", "terracotta", "mud", "moss_block", "dripstone_block", "amethyst_block",
			"podzol", "coarse_dirt", "iron_block", "gold_block", "copper_block", "coal_block", "cactus",
			"dried_kelp_block", "bone_block", "cobweb", "glowstone", "end_stone"
	};

	private static final String[] MOB_IDS = {
			"zombie", "skeleton", "creeper", "spider", "cave_spider", "enderman", "witch", "slime",
			"drowned", "husk", "stray", "cow", "pig", "sheep", "chicken", "rabbit", "horse", "llama",
			"goat", "fox", "cod", "salmon", "squid", "glow_squid", "frog", "bat", "zombie_villager",
			"blaze", "magma_cube", "ghast", "piglin", "hoglin", "zombified_piglin", "wither_skeleton",
			"phantom", "pillager", "silverfish", "guardian", "mooshroom", "turtle"
	};

	private static final String[] ITEM_IDS = {
			"iron_ingot", "gold_ingot", "copper_ingot", "diamond", "emerald", "lapis_lazuli", "redstone",
			"coal", "charcoal", "flint", "bread", "cake", "pumpkin_pie", "cookie", "apple", "golden_apple",
			"bucket", "water_bucket", "lava_bucket", "milk_bucket", "shears", "compass", "clock", "map",
			"book", "bow", "crossbow", "arrow", "shield", "iron_pickaxe", "diamond_pickaxe", "golden_sword",
			"flint_and_steel", "string", "leather", "feather", "egg", "bone", "gunpowder", "spider_eye",
			"ender_pearl", "slime_ball", "glass_bottle", "sugar_cane", "paper", "wheat", "carrot", "potato",
			"beetroot", "fishing_rod", "cooked_beef", "cooked_porkchop", "cooked_chicken", "iron_chestplate",
			"golden_helmet", "leather_boots", "torch", "ladder", "rail", "minecart", "oak_boat", "painting",
			"item_frame", "flower_pot", "campfire", "lantern", "amethyst_shard", "blaze_rod", "magma_cream",
			"nether_wart", "quartz", "honeycomb", "ink_sac", "glow_ink_sac", "saddle", "name_tag", "lead",
			"anvil", "enchanting_table", "jukebox", "note_block", "piston", "observer", "hopper", "tnt",
			"spyglass", "brush", "goat_horn", "rabbit_hide", "rotten_flesh", "kelp", "sweet_berries"
	};

	private final List<Block> blocks = new ArrayList<>();
	private final List<EntityType<?>> mobs = new ArrayList<>();
	private final List<Item> items = new ArrayList<>();

	private Type activeType = null;
	private Object target = null;
	private Component targetName = null;
	private long ticksLeft = 0;
	private long waitTicks = -1;

	/** Löst die Listen oben gegen die echten Registries auf. Unbekannte IDs werden übersprungen. */
	public void resolveTargets() {
		blocks.clear();
		mobs.clear();
		items.clear();
		for (String id : BLOCK_IDS) {
			Identifier key = Identifier.withDefaultNamespace(id);
			if (BuiltInRegistries.BLOCK.containsKey(key)) {
				blocks.add(BuiltInRegistries.BLOCK.getValue(key));
			}
		}
		for (String id : MOB_IDS) {
			Identifier key = Identifier.withDefaultNamespace(id);
			if (BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
				mobs.add(BuiltInRegistries.ENTITY_TYPE.getValue(key));
			}
		}
		for (String id : ITEM_IDS) {
			Identifier key = Identifier.withDefaultNamespace(id);
			if (BuiltInRegistries.ITEM.containsKey(key)) {
				items.add(BuiltInRegistries.ITEM.getValue(key));
			}
		}
	}

	public void reset() {
		activeType = null;
		target = null;
		targetName = null;
		ticksLeft = 0;
		waitTicks = -1;
	}

	private List<Type> enabledTypes(ChallengeSettings s) {
		List<Type> types = new ArrayList<>();
		if (s.forceBlock && !blocks.isEmpty()) {
			types.add(Type.BLOCK);
		}
		if (s.forceMob && !mobs.isEmpty()) {
			types.add(Type.MOB);
		}
		if (s.forceItem && !items.isEmpty()) {
			types.add(Type.ITEM);
		}
		return types;
	}

	public void tick(MinecraftServer server, ChallengeManager manager) {
		List<Type> enabled = enabledTypes(manager.settings());
		if (enabled.isEmpty()) {
			reset();
			return;
		}
		if (activeType != null && !enabled.contains(activeType)) {
			reset();
		}

		if (activeType == null) {
			if (waitTicks < 0) {
				// 2 bis 4 Minuten Pause zwischen den Aufgaben
				waitTicks = (120 + manager.random().nextInt(121)) * 20L;
			}
			waitTicks--;
			if (waitTicks <= 0) {
				startTask(server, manager, enabled.get(manager.random().nextInt(enabled.size())));
			}
			return;
		}

		ticksLeft--;
		if (ticksLeft % 5 == 0) {
			ServerPlayer winner = findCompletingPlayer(server);
			if (winner != null) {
				complete(server, winner);
				return;
			}
		}
		if (ticksLeft <= 0) {
			String name = targetName.getString();
			Type type = activeType;
			reset();
			manager.fail(server, type.title + " nicht geschafft: " + name);
		}
	}

	private void startTask(MinecraftServer server, ChallengeManager manager, Type type) {
		activeType = type;
		switch (type) {
			case BLOCK -> {
				Block block = blocks.get(manager.random().nextInt(blocks.size()));
				target = block;
				targetName = block.getName();
			}
			case MOB -> {
				EntityType<?> mob = mobs.get(manager.random().nextInt(mobs.size()));
				target = mob;
				targetName = mob.getDescription();
			}
			case ITEM -> {
				Item item = items.get(manager.random().nextInt(items.size()));
				target = item;
				targetName = new ItemStack(item).getHoverName();
			}
		}
		ticksLeft = manager.settings().forceMinutes * 60L * 20L;
		waitTicks = -1;

		MutableComponent msg = Component.literal(type.title + ": ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
				.append(Component.literal(type.verb + " ").withStyle(ChatFormatting.WHITE))
				.append(targetName.copy().withStyle(ChatFormatting.AQUA))
				.append(Component.literal(" – ihr habt " + manager.settings().forceMinutes + " Minuten!").withStyle(ChatFormatting.WHITE));
		ChallengeManager.broadcast(server, msg);
		manager.showTitle(server, Component.literal(type.title).withStyle(ChatFormatting.LIGHT_PURPLE),
				targetName.copy().withStyle(ChatFormatting.AQUA));
	}

	private ServerPlayer findCompletingPlayer(MinecraftServer server) {
		if (activeType == Type.MOB) {
			return null; // wird über onMobKilled erledigt
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			if (activeType == Type.BLOCK && target instanceof Block block) {
				ServerLevel level = player.level();
				if (level.getBlockState(player.blockPosition()).is(block)
						|| level.getBlockState(player.blockPosition().below()).is(block)) {
					return player;
				}
			}
			if (activeType == Type.ITEM && target instanceof Item item) {
				if (player.getInventory().countItem(item) > 0) {
					return player;
				}
			}
		}
		return null;
	}

	public void onMobKilled(MinecraftServer server, ChallengeManager manager, ServerPlayer killer, EntityType<?> type) {
		if (activeType == Type.MOB && target == type) {
			complete(server, killer);
		}
	}

	private void complete(MinecraftServer server, ServerPlayer player) {
		ChallengeManager.broadcast(server, Component.literal(player.getName().getString() + " hat es geschafft: ").withStyle(ChatFormatting.GREEN)
				.append(targetName.copy().withStyle(ChatFormatting.AQUA)));
		activeType = null;
		target = null;
		targetName = null;
		ticksLeft = 0;
		waitTicks = -1;
	}

	/** Text für die Actionbar, oder null wenn gerade keine Aufgabe läuft. */
	public Component statusText() {
		if (activeType == null || targetName == null) {
			return null;
		}
		long seconds = Math.max(0, ticksLeft / 20);
		String time = String.format("%d:%02d", seconds / 60, seconds % 60);
		return Component.literal(activeType.title + ": ").withStyle(ChatFormatting.LIGHT_PURPLE)
				.append(targetName.copy().withStyle(ChatFormatting.AQUA))
				.append(Component.literal(" (" + time + ")").withStyle(seconds < 30 ? ChatFormatting.RED : ChatFormatting.GRAY));
	}
}
