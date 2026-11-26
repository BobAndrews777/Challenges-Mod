package de.bob.challenges.extra;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Force-MLG: Alle werden in eine zufällige Höhe teleportiert und bekommen ein MLG-Item in die Hand.
 * Nach der Landung ist das Item wieder weg.
 */
public final class Mlg {
	private static final Mlg INSTANCE = new Mlg();
	private static final String MARK = "challenges_mlg";
	/** Art → {Item, Anzeigename} */
	private static final String[][] KINDS = {
			{"water_bucket", "Wasser-MLG"},
			{"cobweb", "Spinnennetz-MLG"},
			{"slime_block", "Slime-Block-MLG"},
			{"hay_block", "Heuballen-MLG"},
			{"ladder", "Leiter-MLG"},
			{"oak_boat", "Boot-MLG"},
			{"twisting_vines", "Zwirbelranken-MLG"},
			{"sweet_berries", "Süßbeeren-MLG"},
	};

	private final Random random = new Random();
	private final Map<UUID, Run> runs = new HashMap<>();

	private static final class Run {
		ItemStack previousHand = ItemStack.EMPTY;
		int buckets;
		int landedTicks;
		int ticks;
		ServerLevel level;
		final List<BlockPos> pillar = new ArrayList<>();
	}

	public static Mlg get() {
		return INSTANCE;
	}

	public boolean active() {
		return !runs.isEmpty();
	}

	/** Startet einen MLG für alle Spieler. randomKind = Random-MLG, sonst Wasser-MLG. */
	public void start(MinecraftServer server, boolean randomKind) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator() || player.isCreative() || runs.containsKey(player.getUUID())) {
				continue;
			}
			ServerLevel level = player.level();
			boolean nether = level.dimension().equals(Level.NETHER);
			String[] kind = randomKind ? KINDS[random.nextInt(KINDS.length)] : KINDS[0];
			if (nether && kind[0].equals("water_bucket")) {
				kind = KINDS[6];   // Wasser verdampft im Nether
			}
			Double targetY = targetHeight(level, player);
			if (targetY == null) {
				continue;
			}
			Run run = new Run();
			run.level = level;
			run.buckets = player.getInventory().countItem(item("bucket"));
			int slot = player.getInventory().getSelectedSlot();
			run.previousHand = player.getInventory().getItem(slot).copy();
			player.getInventory().setItem(slot, mark(new ItemStack(item(kind[0]))));
			if (kind[0].equals("ladder")) {
				buildPillar(level, player, targetY, run);
			}
			if (player.isPassenger()) {
				player.stopRiding();
			}
			player.teleportTo(level, player.getX(), targetY, player.getZ(), Set.of(), player.getYRot(), 60, true);
			player.setDeltaMovement(0, 0, 0);
			player.fallDistance = 0;
			runs.put(player.getUUID(), run);
			player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
			player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Rette dich mit dem Item in deiner Hand!").withStyle(ChatFormatting.YELLOW)));
			player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(kind[1]).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
		}
	}

	private Double targetHeight(ServerLevel level, ServerPlayer player) {
		int x = player.getBlockX();
		int z = player.getBlockZ();
		if (level.dimension().equals(Level.NETHER)) {
			// Unter der Netherdecke: nach oben bis zur nächsten Decke, maximal 40 Blöcke
			int y = player.getBlockY();
			int top = y;
			for (int up = 1; up <= 40 && y + up < 125; up++) {
				if (!level.getBlockState(new BlockPos(x, y + up + 1, z)).isAir()) {
					break;
				}
				top = y + up;
			}
			return top - y >= 12 ? (double) (y + 12 + random.nextInt(Math.max(1, top - y - 11))) : null;
		}
		level.getChunk(x >> 4, z >> 4);
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		int max = level.getMaxY() - 3;
		return (double) Math.min(max, ground + 40 + random.nextInt(81));
	}

	/** Leiter-MLG: eine Säule neben der Fallstrecke, an die man die Leiter setzen kann */
	private void buildPillar(ServerLevel level, ServerPlayer player, double targetY, Run run) {
		BlockState dirt = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("dirt")).defaultBlockState();
		int x = player.getBlockX() + 1;
		int z = player.getBlockZ();
		for (int y = (int) targetY + 2; y >= level.getMinY(); y--) {
			BlockPos pos = new BlockPos(x, y, z);
			if (!level.getBlockState(pos).isAir()) {
				break;
			}
			level.setBlock(pos, dirt, 2);
			run.pillar.add(pos);
		}
	}

	public void tick(MinecraftServer server) {
		if (runs.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Run>> it = runs.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Run> entry = it.next();
			Run run = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			run.ticks++;
			if (player == null || !player.isAlive()) {
				finish(player, run, false);
				it.remove();
				continue;
			}
			boolean safe = player.onGround() || player.isInWater() || player.isPassenger() || player.onClimbable();
			run.landedTicks = safe && run.ticks > 10 ? run.landedTicks + 1 : 0;
			if (run.landedTicks >= 30 || run.ticks > 40 * 20) {
				finish(player, run, true);
				it.remove();
			}
		}
	}

	private void finish(ServerPlayer player, Run run, boolean alive) {
		for (BlockPos pos : run.pillar) {
			if (run.level.getBlockState(pos).is(BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("dirt")))) {
				run.level.setBlock(pos, BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace("air")).defaultBlockState(), 2);
			}
		}
		if (player == null) {
			return;
		}
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (isMarked(inventory.getItem(i))) {
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}
		// leerer Eimer vom Wasser-MLG
		int extra = inventory.countItem(item("bucket")) - run.buckets;
		for (int i = 0; i < inventory.getContainerSize() && extra > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item("bucket"))) {
				int remove = Math.min(extra, stack.getCount());
				stack.shrink(remove);
				extra -= remove;
			}
		}
		if (!run.previousHand.isEmpty()) {
			int slot = inventory.getSelectedSlot();
			if (inventory.getItem(slot).isEmpty()) {
				inventory.setItem(slot, run.previousHand);
			} else {
				inventory.placeItemBackInInventory(run.previousHand, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		if (alive && player.isAlive()) {
			player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("MLG geschafft!").withStyle(ChatFormatting.GREEN)));
		}
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
	}

	private static ItemStack mark(ItemStack stack) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(MARK, true);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	private static boolean isMarked(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(MARK);
	}
}
