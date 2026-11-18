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
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
			return;
		}
		ServerLevel overworld = server.overworld();
		processPending(server, overworld);
		blockStrangers(overworld);
		if (tick % 20 == 0) {
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

	/** Farbige Partikel zeigen, wem ein Portal gehört. */
	private void showFrames(MinecraftServer server, ServerLevel level) {
		List<String> remove = new ArrayList<>();
		int shown = 0;
		for (Map.Entry<String, String> entry : split.settings().portalOwners.entrySet()) {
			if (shown > 1500) {
				break;
			}
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
			if (color == null) {
				continue;
			}
			level.sendParticles(new DustParticleOptions(color.argb, 1.4f), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					2, 0.3, 0.4, 0.3, 0.0);
			shown++;
		}
		for (String key : remove) {
			split.settings().portalOwners.remove(key);
		}
	}
}
