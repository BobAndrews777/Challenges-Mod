package de.bob.challenges;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Alle Einstellungen und der gespeicherte Stand. Wird als config/challenges.json gespeichert.
 */
public final class ChallengeSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	// Grundfunktionen
	public boolean dragonGoal = true;
	public boolean deathEndsChallenge = true;
	public boolean pauseProtection = true;
	public boolean uhc = false;
	public boolean sharedHearts = false;

	// Zufall
	public boolean randomBlockDrops = false;
	public boolean randomMobDrops = false;
	public boolean randomItem = false;
	public int randomItemSeconds = 60;

	// Schaden
	public boolean jumpDamage = false;
	public int jumpDamageHearts = 1;
	public boolean sneakDeath = false;

	// Force
	public boolean forceBlock = false;
	public boolean forceMob = false;
	public boolean forceItem = false;
	public int forceMinutes = 5;

	// Split-Challenge
	public boolean splitMode = false;
	public int splitMinMinutes = 5;
	public int splitMaxMinutes = 10;
	/** Chance in Prozent, dass statt eines Splits ein Joker kommt. */
	public int jokerChancePercent = 10;

	// Mob-Challenge
	public boolean mobMode = false;
	/** UUID → MobKind-Name */
	public Map<String, String> mobAssignments = new HashMap<>();

	// Mob Army Battle
	public boolean armyActive = false;
	/** FARM, CONFIG, FIGHT oder END */
	public String armyPhase = "FARM";
	public int armyTicksLeft = 0;
	public int armyFarmMinutes = 120;
	public int armyConfigMinutes = 10;
	/** Chance in Prozent, dass beim Abbauen zusätzlich ein Spawn-Ei droppt (in der Farmphase). */
	public double armyEggChance = 2.0;
	/** UUID → Team (ROT/BLAU) */
	public Map<String, String> armyTeams = new HashMap<>();
	/** Team → Mob-ID → Anzahl getötet */
	public Map<String, Map<String, Integer>> armyPools = new HashMap<>();
	/** Team → 3 Wellen, die dieses Team für den GEGNER zusammenstellt (Mob-IDs in Spawn-Reihenfolge) */
	public Map<String, List<List<String>>> armyWaves = new HashMap<>();
	/** Teams, die mit dem Zusammenstellen fertig sind */
	public List<String> armyReady = new ArrayList<>();
	/** Eigene Arena-Map (Name der Struktur vom Konstruktionsblock), leer = eingebaute Halle */
	public String armyArena = "";

	// Gespeicherter Stand
	public long timerTicks = 0;
	public boolean ended = false;
	public long dropSeed = new Random().nextLong();

	// Gespeicherter Stand der Split-Challenge
	public List<String> activeSplits = new ArrayList<>();
	public List<String> removedSplits = new ArrayList<>();
	/** UUID → Farbe (Name aus SplitColor) */
	public Map<String, String> playerColors = new LinkedHashMap<>();
	/** UUID → zuletzt bekannter Spielername */
	public Map<String, String> playerNames = new HashMap<>();
	public int jokersLeft = 0;
	public long nextSplitAt = -1;
	public int placeTurn = 0;
	/** Waffen-/Werkzeugart → UUID des Besitzers */
	public Map<String, String> weaponOwners = new HashMap<>();
	public Map<String, String> toolOwners = new HashMap<>();
	/** Gemeinsames Level beim Level-Split und gesammelte XP je Spieler */
	public int splitLevel = 0;
	public Map<String, Integer> xpBuckets = new HashMap<>();
	/** Portalblock (als Zahl) → UUID des Besitzers */
	public Map<String, String> portalOwners = new HashMap<>();
	/** Split „Skins“: Spielername → Name, dessen Skin er trägt */
	public Map<String, String> skinSwap = new HashMap<>();

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("challenges.json");
	}

	public static ChallengeSettings load() {
		Path file = file();
		if (Files.exists(file)) {
			try {
				ChallengeSettings loaded = GSON.fromJson(Files.readString(file), ChallengeSettings.class);
				if (loaded != null) {
					loaded.clampValues();
					return loaded;
				}
			} catch (Exception e) {
				ChallengesMod.LOGGER.error("Konnte {} nicht lesen, nutze Standardwerte", file, e);
			}
		}
		return new ChallengeSettings();
	}

	public void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(this));
		} catch (IOException e) {
			ChallengesMod.LOGGER.error("Konnte Einstellungen nicht speichern", e);
		}
	}

	public void clampValues() {
		randomItemSeconds = Math.max(10, Math.min(600, randomItemSeconds));
		jumpDamageHearts = Math.max(1, Math.min(10, jumpDamageHearts));
		forceMinutes = Math.max(1, Math.min(20, forceMinutes));
		splitMinMinutes = Math.max(1, Math.min(120, splitMinMinutes));
		splitMaxMinutes = Math.max(splitMinMinutes, Math.min(180, splitMaxMinutes));
		jokerChancePercent = Math.max(0, Math.min(100, jokerChancePercent));
		if (activeSplits == null) activeSplits = new ArrayList<>();
		if (armyTeams == null) armyTeams = new HashMap<>();
		if (armyPools == null) armyPools = new HashMap<>();
		if (armyWaves == null) armyWaves = new HashMap<>();
		if (armyReady == null) armyReady = new ArrayList<>();
		if (armyPhase == null) armyPhase = "FARM";
		if (armyArena == null) armyArena = "";
		armyFarmMinutes = Math.max(1, Math.min(600, armyFarmMinutes));
		armyConfigMinutes = Math.max(1, Math.min(60, armyConfigMinutes));
		armyEggChance = Math.max(0, Math.min(100, armyEggChance));
		if (removedSplits == null) removedSplits = new ArrayList<>();
		if (playerColors == null) playerColors = new LinkedHashMap<>();
		if (playerNames == null) playerNames = new HashMap<>();
		if (weaponOwners == null) weaponOwners = new HashMap<>();
		if (toolOwners == null) toolOwners = new HashMap<>();
		if (xpBuckets == null) xpBuckets = new HashMap<>();
		if (portalOwners == null) portalOwners = new HashMap<>();
		if (skinSwap == null) skinSwap = new HashMap<>();
		if (mobAssignments == null) mobAssignments = new HashMap<>();
	}
}
