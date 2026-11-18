package de.bob.challenges.split;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Split 21 „Chunks“: Jeder steckt in einem 9×9-Bereich, der mit ihm mitwandert.
 * Die Bereiche blockieren sich gegenseitig – man kommt sich nicht näher als 9 Blöcke.
 */
public final class ChunkSplit {
	/** Kantenlänge eines Bereichs */
	private static final double SIZE = 9.0;
	private static final double HALF = SIZE / 2.0;

	private final SplitManager split;
	private final Map<UUID, double[]> lastPos = new HashMap<>();
	private long tick = 0;

	ChunkSplit(SplitManager split) {
		this.split = split;
	}

	/** > 0 heißt: die Bereiche überlappen (je größer, desto tiefer). */
	private static double overlap(double ax, double az, double bx, double bz) {
		return Math.min(SIZE - Math.abs(ax - bx), SIZE - Math.abs(az - bz));
	}

	void tick(MinecraftServer server) {
		tick++;
		if (!split.isActive(SplitType.CHUNKS)) {
			lastPos.clear();
			return;
		}
		List<ServerPlayer> players = split.coloredPlayers(server);
		for (ServerPlayer player : players) {
			double[] last = lastPos.get(player.getUUID());
			if (last == null) {
				continue;
			}
			for (ServerPlayer other : players) {
				if (other == player || other.level() != player.level()) {
					continue;
				}
				double[] otherLast = lastPos.getOrDefault(other.getUUID(), new double[] {other.getX(), other.getY(), other.getZ()});
				double before = overlap(last[0], last[2], otherLast[0], otherLast[2]);
				double now = overlap(player.getX(), player.getZ(), otherLast[0], otherLast[2]);
				// Hineinlaufen verboten, Herauslaufen erlaubt
				if (now > 0 && now > before + 0.0001) {
					player.teleportTo(player.level(), last[0], player.getY(), last[2], Set.of(), player.getYRot(), player.getXRot(), true);
					player.connection.send(new ClientboundSetActionBarTextPacket(
							Component.literal("Hier beginnt der Bereich von " + other.getName().getString() + "!").withStyle(ChatFormatting.RED)));
					break;
				}
			}
		}
		for (ServerPlayer player : players) {
			lastPos.put(player.getUUID(), new double[] {player.getX(), player.getY(), player.getZ()});
		}
		if (tick % 10 == 0) {
			for (ServerPlayer player : players) {
				drawBorder(player);
			}
		}
	}

	/** Der Rand jedes Bereichs als Partikel in der Spielerfarbe. */
	private void drawBorder(ServerPlayer player) {
		SplitColor color = split.colorOf(player);
		if (color == null) {
			return;
		}
		ServerLevel level = player.level();
		DustParticleOptions dust = new DustParticleOptions(color.argb, 1.0f);
		double cx = player.getX();
		double cz = player.getZ();
		for (double y : new double[] {player.getY() + 0.3, player.getY() + 1.3, player.getY() + 2.3}) {
			for (double d = -HALF; d <= HALF; d += 1.0) {
				level.sendParticles(dust, cx + d, y, cz - HALF, 1, 0, 0, 0, 0);
				level.sendParticles(dust, cx + d, y, cz + HALF, 1, 0, 0, 0, 0);
				level.sendParticles(dust, cx - HALF, y, cz + d, 1, 0, 0, 0, 0);
				level.sendParticles(dust, cx + HALF, y, cz + d, 1, 0, 0, 0, 0);
			}
		}
	}
}
