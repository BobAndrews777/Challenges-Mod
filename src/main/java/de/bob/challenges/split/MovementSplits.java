package de.bob.challenges.split;

import java.util.List;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Splits 9 (Sneaken), 12 (Sprinten) und 20 (Springen):
 * Es klappt nur, wenn ALLE gleichzeitig die Taste drücken.
 * Außerdem Split 5 (Block abbauen): Jeder baut schneller ab, weil jeder nur seinen Anteil schafft.
 */
public final class MovementSplits {
	private static final double WALK_SPEED = 0.1;
	/** Sprinten gibt +30 %. Mit dieser Grundgeschwindigkeit ist Sprinten nur noch so schnell wie Gehen. */
	private static final double NO_SPRINT_SPEED = WALK_SPEED / 1.3;
	private static final double JUMP_STRENGTH = 0.42;

	private final SplitManager split;
	private MovementPayload lastSent = null;
	private boolean modified = false;

	MovementSplits(SplitManager split) {
		this.split = split;
	}

	void tick(MinecraftServer server, long tick) {
		boolean sneak = split.isActive(SplitType.SNEAK);
		boolean sprint = split.isActive(SplitType.SPRINT);
		boolean jump = split.isActive(SplitType.JUMP);
		boolean mining = split.isActive(SplitType.MINING);
		List<ServerPlayer> players = split.coloredPlayers(server);

		int sneakMask = 0;
		int sprintMask = 0;
		int jumpMask = 0;
		boolean allSneak = !players.isEmpty();
		boolean allSprint = !players.isEmpty();
		boolean allJump = !players.isEmpty();
		for (ServerPlayer player : players) {
			int bit = 1 << split.colorOf(player).ordinal();
			var input = player.getLastClientInput();
			if (input.shift()) sneakMask |= bit; else allSneak = false;
			if (input.sprint()) sprintMask |= bit; else allSprint = false;
			if (input.jump()) jumpMask |= bit; else allJump = false;
		}

		if (sprint || jump || mining || modified) {
			int n = split.playerCount();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (de.bob.challenges.mobs.MobChallenge.get().kindOf(player) != null) {
					continue; // Die Mob-Challenge bestimmt diese Werte selbst
				}
				boolean colored = split.colorOf(player) != null;
				setBase(player.getAttribute(Attributes.MOVEMENT_SPEED),
						sprint && colored && player.isSprinting() && !allSprint ? NO_SPRINT_SPEED : WALK_SPEED);
				setBase(player.getAttribute(Attributes.JUMP_STRENGTH),
						jump && colored && !allJump ? 0.0 : JUMP_STRENGTH);
				setBase(player.getAttribute(Attributes.BLOCK_BREAK_SPEED),
						mining && colored ? n : 1.0);
			}
			modified = sprint || jump || mining;
		}

		if (tick % 2 == 0) {
			int flags = (sneak ? MovementPayload.SNEAK : 0) | (sprint ? MovementPayload.SPRINT : 0) | (jump ? MovementPayload.JUMP : 0);
			MovementPayload payload = new MovementPayload(flags, sneakMask, sprintMask, jumpMask, allSneak);
			if (!payload.equals(lastSent)) {
				lastSent = payload;
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (ServerPlayNetworking.canSend(player, MovementPayload.TYPE)) {
						ServerPlayNetworking.send(player, payload);
					}
				}
			}
		}
	}

	private static void setBase(AttributeInstance attribute, double value) {
		if (attribute != null && Math.abs(attribute.getBaseValue() - value) > 0.0001) {
			attribute.setBaseValue(value);
		}
	}
}
