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
	public int splitJokers = 1;

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
	public int jokersLeft = 1;
	public long nextSplitAt = -1;
	public int placeTurn = 0;
	/** Waffen-/Werkzeugart → UUID des Besitzers */
	public Map<String, String> weaponOwners = new HashMap<>();
	public Map<String, String> toolOwners = new HashMap<>();

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
		splitJokers = Math.max(0, Math.min(10, splitJokers));
		if (activeSplits == null) activeSplits = new ArrayList<>();
		if (removedSplits == null) removedSplits = new ArrayList<>();
		if (playerColors == null) playerColors = new LinkedHashMap<>();
		if (playerNames == null) playerNames = new HashMap<>();
		if (weaponOwners == null) weaponOwners = new HashMap<>();
		if (toolOwners == null) toolOwners = new HashMap<>();
	}
}
