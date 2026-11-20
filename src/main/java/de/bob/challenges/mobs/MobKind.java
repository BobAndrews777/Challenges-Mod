package de.bob.challenges.mobs;

import java.util.Set;

/**
 * Alle Mobs aus Bobs Liste mit ihren Werten.
 * {@code ready = true}: wird schon ausgelost. Die übrigen kommen in Etappe 2 (Reiten, Hüpfen, Lava-Laufen).
 */
public enum MobKind {
	PARROT("Papagei", "parrot", 3, 0.9, true, Set.of("wheat_seeds"),
			Flags.FLY, "Tanzen", null),
	STRIDER("Schreiter", "strider", 10, 1.7, false, null,
			Flags.FIRE_IMMUNE, null, null),
	DONKEY("Esel", "donkey", 13, 1.5, false, Set.of("wheat"),
			0, null, null),
	PIG("Schwein", "pig", 5, 0.9, false, null,
			0, "Grunzen", null),
	BEE("Biene", "bee", 5, 0.6, true, Flags.FLOWERS,
			Flags.FLY, "Bienenschwarm", null),
	FROG("Frosch", "frog", 5, 0.5, true, null,
			Flags.WATER_BREATHING, null, null),
	GOAT("Ziege", "goat", 5, 1.3, true, Set.of("wheat"),
			0, "Dash", null),
	ARMADILLO("Gürteltier", "armadillo", 6, 0.65, true, Set.of("spider_eye"),
			0, "Einrollen", null),
	CAT("Katze", "cat", 5, 0.7, true, null,
			Flags.NO_FALL, null, null),
	CAMEL("Kamel", "camel", 16, 2.375, false, null,
			0, null, null),
	IRON_GOLEM("Eisengolem", "iron_golem", 50, 2.7, true, null,
			Flags.NO_FALL | Flags.IRON_HEAL_ONLY, null, null),
	CREEPER("Creeper", "creeper", 10, 1.7, true, Set.of("gunpowder"),
			0, "Explodieren", null),
	ENDERMAN("Enderman", "enderman", 20, 2.9, true, null,
			Flags.WATER_DAMAGE, "Teleportieren", null),
	BREEZE("Breeze", "breeze", 15, 1.77, true, null,
			Flags.NO_FALL, "Windkugel", null),
	BLAZE("Blaze", "blaze", 10, 1.8, true, null,
			Flags.NO_FALL | Flags.FIRE_IMMUNE, "Feuerbälle", null),
	ELDER_GUARDIAN("Elder Guardian", "elder_guardian", 40, 2.0, true,
			Set.of("cod", "salmon", "cooked_cod", "cooked_salmon", "tropical_fish", "pufferfish"),
			Flags.WATER_BREATHING, "Laserstrahl", null),
	GHAST("Ghast", "ghast", 5, 4.0, true, Set.of("lava_bucket", "magma_block", "magma_cream"),
			Flags.FLY | Flags.NO_FALL | Flags.FIRE_IMMUNE, "Feuerball", null),
	MAGMA_CUBE("Magma Cube", "magma_cube", 8, 2.04, false, Set.of("lava_bucket", "magma_block", "magma_cream"),
			Flags.NO_FALL | Flags.FIRE_IMMUNE, "Mini-Magmawürfel", null),
	SILVERFISH("Silberfisch", "silverfish", 4, 0.3, true, Set.of("stone", "cobblestone"),
			0, "Silberfische rufen", null),
	WARDEN("Warden", "warden", 250, 2.9, true, Set.of("glow_berries"),
			Flags.DARKNESS, "Sonic Boom", null),
	EVOKER("Evoker", "evoker", 12, 1.95, true, null,
			0, "Klauen", "Vexe"),
	SHULKER("Shulker", "shulker", 15, 1.0, true, null,
			Flags.WATER_DAMAGE | Flags.NO_WALK, "Teleportieren", "Schwebe-Geschoss");

	public static final class Flags {
		public static final int FLY = 1;
		public static final int NO_FALL = 2;
		public static final int FIRE_IMMUNE = 4;
		public static final int WATER_BREATHING = 8;
		public static final int WATER_DAMAGE = 16;
		public static final int DARKNESS = 32;
		public static final int IRON_HEAL_ONLY = 64;
		public static final int NO_WALK = 128;

		/** Platzhalter für „alle Blumen“ */
		public static final Set<String> FLOWERS = Set.of("#flowers");

		private Flags() {
		}
	}

	public final String displayName;
	/** Vanilla-ID des Mobs, z. B. "enderman" */
	public final String entityId;
	public final int hearts;
	/** Höhe des echten Mobs in Blöcken – daraus wird die Spielergröße berechnet */
	public final double height;
	/** Wird schon ausgelost (alles Nötige ist fertig) */
	public final boolean ready;
	/** Erlaubtes Essen (Item-IDs), null = normales Essen */
	public final Set<String> foods;
	public final int flags;
	public final String ability1;
	public final String ability2;

	MobKind(String displayName, String entityId, int hearts, double height, boolean ready, Set<String> foods,
			int flags, String ability1, String ability2) {
		this.displayName = displayName;
		this.entityId = entityId;
		this.hearts = hearts;
		this.height = height;
		this.ready = ready;
		this.foods = foods;
		this.flags = flags;
		this.ability1 = ability1;
		this.ability2 = ability2;
	}

	public boolean has(int flag) {
		return (flags & flag) != 0;
	}

	/** Spielergröße relativ zum normalen Spieler (1,8 Blöcke) */
	public double scale() {
		return height / 1.8;
	}

	/**
	 * Inventar-Plätze nach Größe: winzig nur Hotbar (9), klein 18, sonst alle 36.
	 */
	public int inventorySlots() {
		if (height < 0.7) {
			return 9;
		}
		if (height < 1.4) {
			return 18;
		}
		return 36;
	}

	/** Abbau-Tempo nach Größe: kleine Mobs langsamer, große schneller. */
	public double breakSpeed() {
		if (height < 0.7) {
			return 0.5;
		}
		if (height < 1.4) {
			return 0.75;
		}
		if (height < 2.0) {
			return 1.0;
		}
		if (height < 3.0) {
			return 1.6;
		}
		return 2.0;
	}

	/** Sprungkraft (Vanilla-Spieler 0,42 ≈ 1,25 Blöcke) */
	public double jumpStrength() {
		return switch (this) {
			case FROG -> 0.85;   // ca. 4 Blöcke
			case GOAT -> 1.35;   // ca. 10 Blöcke
			default -> has(Flags.NO_WALK) ? 0.0 : 0.42;
		};
	}

	/** Sichere Fallhöhe in Blöcken (Vanilla 3) */
	public double safeFallDistance() {
		return switch (this) {
			case FROG -> 20.0;
			case GOAT -> 13.0;   // 5 Herzen = 10 Schaden weniger
			default -> 3.0;
		};
	}

	public double fallDamageMultiplier() {
		if (has(Flags.NO_FALL)) {
			return 0.0;
		}
		return this == FROG ? 0.5 : 1.0;
	}

	public static MobKind byName(String name) {
		for (MobKind kind : values()) {
			if (kind.name().equalsIgnoreCase(name) || kind.displayName.equalsIgnoreCase(name)) {
				return kind;
			}
		}
		return null;
	}
}
