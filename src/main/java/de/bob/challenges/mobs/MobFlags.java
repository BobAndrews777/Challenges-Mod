package de.bob.challenges.mobs;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Gemeinsamer Zustand für Mixins, die auf Server und Client laufen. */
public final class MobFlags {
	/** Auf dem Client: welcher Mob der eigene Spieler ist (Anzeigename, leer = keiner) */
	public static volatile String clientKind = "";

	private MobFlags() {
	}

	/** Schreiter und Magma Cube können auf Lava stehen. */
	public static boolean walksOnLava(Entity entity) {
		if (entity instanceof ServerPlayer player) {
			MobKind kind = MobChallenge.get().kindOf(player);
			return kind == MobKind.STRIDER || kind == MobKind.MAGMA_CUBE;
		}
		if (entity instanceof net.minecraft.world.entity.player.Player) {
			String kind = clientKind;
			return kind.equals(MobKind.STRIDER.displayName) || kind.equals(MobKind.MAGMA_CUBE.displayName);
		}
		return false;
	}
}
