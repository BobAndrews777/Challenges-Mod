package de.bob.challenges;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

	// Gespeicherter Stand
	public long timerTicks = 0;
	public boolean ended = false;
	public long dropSeed = new Random().nextLong();

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
	}
}
