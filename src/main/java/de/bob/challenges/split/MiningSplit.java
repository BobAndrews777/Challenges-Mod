package de.bob.challenges.split;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Split 5 „Block abbauen“: Ein Block bricht erst, wenn jeder seinen Anteil abgebaut hat.
 * Dafür baut jeder entsprechend schneller ab (siehe MovementSplits). Der Fortschritt bleibt als Risse sichtbar,
 * jeder erledigte Anteil sprüht kurz in der Spielerfarbe.
 */
public final class MiningSplit {
	private record Progress(ServerLevel level, BlockPos pos, BlockState state, Set<Integer> colors, int crackId) {
	}

	private final SplitManager split;
	private final Map<String, Progress> progress = new HashMap<>();
	private int nextCrackId = -1000;
	private long tick = 0;

	MiningSplit(SplitManager split) {
		this.split = split;
	}

	/** Aus PlayerBlockBreakEvents.BEFORE: false = Block bleibt noch stehen. */
	public boolean onBreak(Level level, Player player, BlockPos pos, BlockState state) {
		if (!split.isActive(SplitType.MINING) || !(player instanceof ServerPlayer serverPlayer)
				|| !(level instanceof ServerLevel serverLevel) || player.isCreative()) {
			return true;
		}
		SplitColor color = split.colorOf(player);
		if (color == null) {
			return true;
		}
		String key = serverLevel.dimension() + "|" + pos.asLong();
		Progress entry = progress.get(key);
		if (entry == null || !entry.state().equals(state)) {
			entry = new Progress(serverLevel, pos.immutable(), state, new HashSet<>(), nextCrackId--);
			progress.put(key, entry);
		}
		if (!entry.colors().add(color.ordinal())) {
			serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(
					Component.literal("Deinen Teil hast du schon – jetzt sind die anderen dran!").withStyle(ChatFormatting.YELLOW)));
			showCracks(entry);
			return false;
		}

		int needed = split.settings().playerColors.size();
		serverLevel.sendParticles(new DustParticleOptions(color.argb, 1.5f), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
				16, 0.35, 0.35, 0.35, 0.0);
		if (entry.colors().size() >= needed) {
			progress.remove(key);
			serverLevel.destroyBlockProgress(entry.crackId(), pos, -1);
			return true;
		}
		serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(
				Component.literal("Dein Anteil ist geschafft (" + entry.colors().size() + "/" + needed + ")").withStyle(color.chat)));
		showCracks(entry);
		return false;
	}

	private void showCracks(Progress entry) {
		int needed = Math.max(1, split.settings().playerColors.size());
		int stage = Math.min(9, entry.colors().size() * 10 / needed);
		entry.level().destroyBlockProgress(entry.crackId(), entry.pos(), stage);
	}

	void tick() {
		tick++;
		if (!split.isActive(SplitType.MINING)) {
			for (Progress entry : progress.values()) {
				entry.level().destroyBlockProgress(entry.crackId(), entry.pos(), -1);
			}
			progress.clear();
			return;
		}
		if (tick % 100 != 0) {
			return;
		}
		// Risse auffrischen (sonst verschwinden sie nach einer Weile) und Veraltetes aufräumen
		Iterator<Progress> it = progress.values().iterator();
		while (it.hasNext()) {
			Progress entry = it.next();
			if (!entry.level().getBlockState(entry.pos()).equals(entry.state())) {
				entry.level().destroyBlockProgress(entry.crackId(), entry.pos(), -1);
				it.remove();
			} else {
				showCracks(entry);
			}
		}
	}
}
