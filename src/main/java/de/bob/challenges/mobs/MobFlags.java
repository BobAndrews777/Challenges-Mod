package de.bob.challenges.mobs;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Gemeinsamer Zustand für Mixins, die auf Server und Client laufen. */
public final class MobFlags {
	/** Auf dem Client: welcher Mob der eigene Spieler ist (Anzeigename, leer = keiner) */
	public static volatile String clientKind = "";

	/** Spieler-UUID → Hitbox {Breite, Höhe} des Mobs (Server und Client) */
	public static final java.util.Map<java.util.UUID, float[]> dims = new java.util.concurrent.ConcurrentHashMap<>();

	/** Wird gesetzt, sobald der Hitbox-Mixin greift – dann ist keine Größen-Skalierung mehr nötig. */
	public static volatile boolean dimsMixinWorks = false;

	/** Auf dem Client: Entity-ID des eigenen Mob-Kostüms (-1 = keins) */
	public static volatile int ownDisguiseId = -1;

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
