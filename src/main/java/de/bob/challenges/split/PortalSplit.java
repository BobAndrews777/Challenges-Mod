package de.bob.challenges.split;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import de.bob.challenges.ChallengeManager;

/**
 * Split 18 „Netherportale“: Ein Portal in der Oberwelt gehört dem, der es anzündet.
 * Nur er kommt hindurch. Portale von vor dem Split bleiben für alle offen.
 */
public final class PortalSplit {
	private record Pending(UUID player, BlockPos pos, long atTick) {
	}

	private final SplitManager split;
	private final List<Pending> pending = new ArrayList<>();
	private final Map<UUID, double[]> lastSafe = new HashMap<>();
	/** Rahmenblöcke, die den Spielern gerade farbig vorgegaukelt werden */
	private Set<BlockPos> shownFrames = new HashSet<>();
	private long tick = 0;

	PortalSplit(SplitManager split) {
		this.split = split;
	}

	private static boolean isPortal(ServerLevel level, BlockPos pos) {
		return "nether_portal".equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath());
	}

	/** Feuerzeug oder Feuerkugel an einem Block benutzt → kurz danach nach einem neuen Portal suchen. */
	public InteractionResult onUseBlock(Player player, Level level, BlockHitResult hit) {
		if (!split.isActive(SplitType.PORTALS) || !(player instanceof ServerPlayer serverPlayer)
				|| level.dimension() != Level.OVERWORLD) {
			return InteractionResult.PASS;
		}
		String item = BuiltInRegistries.ITEM.getKey(serverPlayer.getMainHandItem().getItem()).getPath();
		String offhand = BuiltInRegistries.ITEM.getKey(serverPlayer.getOffhandItem().getItem()).getPath();
		if (item.equals("flint_and_steel") || item.equals("fire_charge")
				|| offhand.equals("flint_and_steel") || offhand.equals("fire_charge")) {
			pending.add(new Pending(serverPlayer.getUUID(), hit.getBlockPos().relative(hit.getDirection()), tick + 2));
		}
		return InteractionResult.PASS;
	}

	void tick(MinecraftServer server) {
		tick++;
		if (!split.isActive(SplitType.PORTALS)) {
			pending.clear();
			lastSafe.clear();
			if (!shownFrames.isEmpty()) {
				restoreFrames(server.overworld(), shownFrames);
				shownFrames = new HashSet<>();
			}
			return;
		}
		ServerLevel overworld = server.overworld();
		processPending(server, overworld);
		blockStrangers(overworld);
		if (tick % 40 == 0) {
			showFrames(server, overworld);
		}
	}

	private void processPending(MinecraftServer server, ServerLevel level) {
		Iterator<Pending> it = pending.iterator();
		while (it.hasNext()) {
			Pending p = it.next();
			if (tick < p.atTick()) {
				continue;
			}
			it.remove();
			BlockPos start = null;
			for (BlockPos pos : BlockPos.betweenClosed(p.pos().offset(-2, -2, -2), p.pos().offset(2, 2, 2))) {
				if (isPortal(level, pos)) {
					start = pos.immutable();
					break;
				}
			}
			if (start == null) {
				continue;
			}
			Set<BlockPos> portal = floodFill(level, start);
			Map<String, String> owners = split.settings().portalOwners;
			for (BlockPos pos : portal) {
				owners.put(String.valueOf(pos.asLong()), p.player().toString());
			}
			String name = split.settings().playerNames.getOrDefault(p.player().toString(), "?");
			ChallengeManager.broadcast(server, Component.literal(name + " hat ein Netherportal entzündet – nur " + name + " kann es benutzen.")
					.withStyle(ChatFormatting.LIGHT_PURPLE));
			ChallengeManager.get().save();
		}
	}

	private static Set<BlockPos> floodFill(ServerLevel level, BlockPos start) {
		Set<BlockPos> result = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		while (!queue.isEmpty() && result.size() < 600) {
			BlockPos pos = queue.poll();
			if (result.contains(pos) || !isPortal(level, pos)) {
				continue;
			}
			result.add(pos);
			for (Direction direction : Direction.values()) {
				queue.add(pos.relative(direction));
			}
		}
		return result;
	}

	private String ownerAt(ServerLevel level, BlockPos pos) {
		String owner = split.settings().portalOwners.get(String.valueOf(pos.asLong()));
		if (owner != null && !isPortal(level, pos)) {
			split.settings().portalOwners.remove(String.valueOf(pos.asLong()));
			return null;
		}
		return owner;
	}

	/** Wer in ein fremdes Portal läuft, wird zurückgesetzt, bevor es ihn teleportiert. */
	private void blockStrangers(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			BlockPos feet = player.blockPosition();
			String owner = ownerAt(level, feet);
			if (owner == null) {
				owner = ownerAt(level, feet.above());
			}
			if (owner != null && !owner.equals(player.getUUID().toString())) {
				double[] safe = lastSafe.get(player.getUUID());
				if (safe != null) {
					player.teleportTo(level, safe[0], safe[1], safe[2], Set.of(), player.getYRot(), player.getXRot(), true);
				}
				String name = split.settings().playerNames.getOrDefault(owner, "jemand anderem");
				player.connection.send(new ClientboundSetActionBarTextPacket(
						Component.literal("Dieses Portal gehört " + name + "!").withStyle(ChatFormatting.RED)));
			} else if (!isPortal(level, feet) && !isPortal(level, feet.above())) {
				lastSafe.put(player.getUUID(), new double[] {player.getX(), player.getY(), player.getZ()});
			}
		}
	}

	private static boolean isObsidian(ServerLevel level, BlockPos pos) {
		return "obsidian".equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath());
	}

	private static BlockState colorBlock(SplitColor color) {
		String id = switch (color) {
			case RED -> "red_concrete";
			case YELLOW -> "yellow_concrete";
			case BLUE -> "blue_concrete";
			case GREEN -> "lime_concrete";
		};
		Identifier key = Identifier.withDefaultNamespace(id);
		return BuiltInRegistries.BLOCK.containsKey(key) ? BuiltInRegistries.BLOCK.getValue(key).defaultBlockState() : null;
	}

	/**
	 * Der Obsidian-Rahmen eines Portals wird bei den Spielern in der Farbe des Besitzers angezeigt.
	 * Auf dem Server bleibt es echter Obsidian – das Portal funktioniert ganz normal.
	 */
	private void showFrames(MinecraftServer server, ServerLevel level) {
		List<String> remove = new ArrayList<>();
		Map<BlockPos, BlockState> frames = new HashMap<>();
		for (Map.Entry<String, String> entry : split.settings().portalOwners.entrySet()) {
			BlockPos pos;
			try {
				pos = BlockPos.of(Long.parseLong(entry.getKey()));
			} catch (NumberFormatException e) {
				remove.add(entry.getKey());
				continue;
			}
			if (!level.isLoaded(pos)) {
				continue;
			}
			if (!isPortal(level, pos)) {
				remove.add(entry.getKey());
				continue;
			}
			SplitColor color = split.colorOfUuid(entry.getValue());
			BlockState state = color == null ? null : colorBlock(color);
			if (state == null) {
				continue;
			}
			for (BlockPos neighbor : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
				if (!frames.containsKey(neighbor) && isObsidian(level, neighbor)) {
					frames.put(neighbor.immutable(), state);
				}
			}
		}
		for (String key : remove) {
			split.settings().portalOwners.remove(key);
		}

		for (ServerPlayer player : level.players()) {
			for (Map.Entry<BlockPos, BlockState> frame : frames.entrySet()) {
				if (player.blockPosition().distSqr(frame.getKey()) < 96 * 96) {
					player.connection.send(new ClientboundBlockUpdatePacket(frame.getKey(), frame.getValue()));
				}
			}
		}

		// Rahmen, die nicht mehr zu einem Portal gehören, wieder echt anzeigen
		Set<BlockPos> stale = new HashSet<>(shownFrames);
		stale.removeAll(frames.keySet());
		restoreFrames(level, stale);
		shownFrames = new HashSet<>(frames.keySet());
	}

	private static void restoreFrames(ServerLevel level, Set<BlockPos> positions) {
		for (ServerPlayer player : level.players()) {
			for (BlockPos pos : positions) {
				player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
			}
		}
	}
}
