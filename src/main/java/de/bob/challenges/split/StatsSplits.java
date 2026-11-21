package de.bob.challenges.split;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodData;

import de.bob.challenges.ChallengeSettings;

/**
 * Splits 2 (Level), 4 (Herzen), 6 (Hunger) und 16 (Atemluft):
 * Jeder bekommt nur seinen Anteil, und alle sehen die Anteile der anderen.
 */
public final class StatsSplits {
	private static final float FULL_HEALTH = 20.0f;
	private static final int FULL_AIR = 300;

	private final SplitManager split;
	private final Map<UUID, Integer> lastFood = new HashMap<>();
	private final Map<UUID, Integer> lastTotalXp = new HashMap<>();
	private boolean statsWereSent = false;

	StatsSplits(SplitManager split) {
		this.split = split;
	}

	// ------------------------------------------------------------------
	// Anteile
	// ------------------------------------------------------------------

	/** Lebenspunkte pro Spieler: 4 → 5 (2,5 Herzen), 3 → 6 (3 Herzen), 2 → 10. */
	public static float healthShare(int players) {
		return switch (players) {
			case 0, 1 -> 20.0f;
			case 2 -> 10.0f;
			case 3 -> 6.0f;
			default -> 5.0f;
		};
	}

	/** Hungerpunkte pro Spieler (gleiche Aufteilung wie die Herzen). */
	public static float foodShare(int players) {
		return healthShare(players);
	}

	public static int airShare(int players) {
		return FULL_AIR / Math.max(1, Math.min(4, players));
	}

	/** Vanilla-Formel: XP-Punkte für das nächste Level. */
	public static int xpForLevel(int level) {
		if (level >= 30) {
			return 112 + (level - 30) * 9;
		}
		if (level >= 15) {
			return 37 + (level - 15) * 5;
		}
		return 7 + level * 2;
	}

	// ------------------------------------------------------------------
	// Tick
	// ------------------------------------------------------------------

	/** Läuft immer (auch bei pausiertem Timer), damit Werte nach einem Joker wieder normal werden. */
	void alwaysTick(MinecraftServer server, long tick) {
		if (tick % 10 == 0) {
			applyMaxHealth(server);
		}
		if (tick % 5 == 0) {
			sendStats(server);
		}
	}

	/** Läuft nur, solange der Timer läuft. */
	void runningTick(MinecraftServer server, long tick) {
		if (split.isActive(SplitType.HUNGER)) {
			tickHunger(server, tick);
		} else {
			lastFood.clear();
		}
		if (split.isActive(SplitType.AIR)) {
			int share = airShare(split.playerCount());
			for (ServerPlayer player : split.coloredPlayers(server)) {
				if (player.getAirSupply() > share) {
					player.setAirSupply(share);
				}
			}
		}
		if (split.isActive(SplitType.LEVEL)) {
			tickLevel(server);
		} else {
			lastTotalXp.clear();
		}
	}

	// ---- Herzen ----

	private void applyMaxHealth(MinecraftServer server) {
		boolean active = split.isActive(SplitType.HEARTS);
		float share = healthShare(split.playerCount());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (de.bob.challenges.mobs.MobChallenge.get().kindOf(player) != null) {
				continue; // Die Mob-Challenge bestimmt die Leben selbst
			}
			float desired = active && split.colorOf(player) != null ? share : FULL_HEALTH;
			AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
			if (maxHealth != null && Math.abs(maxHealth.getBaseValue() - desired) > 0.01) {
				maxHealth.setBaseValue(desired);
				if (player.getHealth() > desired) {
					player.setHealth(desired);
				}
			}
		}
	}

	// ---- Hunger ----

	/**
	 * Intern hat jeder weiter 20 Hungerpunkte, angezeigt wird aber nur sein Anteil.
	 * Deshalb zählt jede Änderung (Essen und Verbrauch) mal Spieleranzahl.
	 * Regeneriert wird nur, wenn ALLE satt sind.
	 */
	private void tickHunger(MinecraftServer server, long tick) {
		int n = split.playerCount();
		List<ServerPlayer> players = split.coloredPlayers(server);
		boolean allFull = !players.isEmpty();
		for (ServerPlayer player : players) {
			FoodData food = player.getFoodData();
			int level = food.getFoodLevel();
			Integer last = lastFood.get(player.getUUID());
			if (last != null && level != last) {
				int scaled = Math.max(0, Math.min(20, last + (level - last) * n));
				if (scaled != level) {
					food.setFoodLevel(scaled);
					level = scaled;
				}
			}
			lastFood.put(player.getUUID(), level);
			if (level < 18) {
				allFull = false;
			}
		}
		// Gemeinsame Regeneration: alle 4 Sekunden 1 Lebenspunkt, kostet je einen angezeigten Hungerpunkt
		if (allFull && tick % 80 == 0) {
			for (ServerPlayer player : players) {
				if (player.getHealth() < player.getMaxHealth()) {
					player.heal(1.0f);
					int newLevel = Math.max(0, player.getFoodData().getFoodLevel() - n);
					player.getFoodData().setFoodLevel(newLevel);
					lastFood.put(player.getUUID(), newLevel);
				}
			}
		}
	}

	/** Echte Hungerpunkte in angezeigte umrechnen. */
	private static float shownFood(int realFood, int players) {
		return realFood / (float) Math.max(1, players);
	}

	// ---- Level ----

	/** Wird beim Aktivieren aufgerufen: gemeinsames Level = niedrigstes aktuelles Level. */
	void startLevel(MinecraftServer server) {
		ChallengeSettings s = split.settings();
		int min = Integer.MAX_VALUE;
		for (ServerPlayer player : split.coloredPlayers(server)) {
			min = Math.min(min, player.experienceLevel);
		}
		s.splitLevel = min == Integer.MAX_VALUE ? 0 : min;
		s.xpBuckets.clear();
		lastTotalXp.clear();
	}

	private void tickLevel(MinecraftServer server) {
		ChallengeSettings s = split.settings();
		List<ServerPlayer> players = split.coloredPlayers(server);
		if (players.isEmpty()) {
			return;
		}
		int n = Math.max(1, split.playerCount());
		int need = Math.max(1, (int) Math.ceil(xpForLevel(s.splitLevel) / (double) n));

		// XP-Gewinne einsammeln und ausgegebene Level (Verzaubern, Amboss) erkennen
		for (ServerPlayer player : players) {
			String key = player.getUUID().toString();
			int total = player.totalExperience;
			Integer last = lastTotalXp.put(player.getUUID(), total);
			if (last != null && total > last) {
				int bucket = s.xpBuckets.getOrDefault(key, 0);
				s.xpBuckets.put(key, Math.min(need, bucket + (total - last)));
			}
			if (player.experienceLevel < s.splitLevel) {
				s.splitLevel = Math.max(0, player.experienceLevel);
			}
		}

		// Level steigt erst, wenn jeder seinen Teil der Leiste gefüllt hat
		need = Math.max(1, (int) Math.ceil(xpForLevel(s.splitLevel) / (double) n));
		boolean allFull = true;
		for (ServerPlayer player : players) {
			if (s.xpBuckets.getOrDefault(player.getUUID().toString(), 0) < need) {
				allFull = false;
			}
		}
		if (allFull && players.size() >= n) {
			s.splitLevel++;
			s.xpBuckets.clear();
			need = Math.max(1, (int) Math.ceil(xpForLevel(s.splitLevel) / (double) n));
		}

		// Allen dasselbe Level und denselben Gesamtfortschritt anzeigen
		int sum = 0;
		for (int value : s.xpBuckets.values()) {
			sum += Math.min(need, value);
		}
		float progress = Math.min(0.999f, sum / (float) (need * n));
		for (ServerPlayer player : players) {
			if (player.experienceLevel != s.splitLevel || Math.abs(player.experienceProgress - progress) > 0.001f) {
				player.experienceLevel = s.splitLevel;
				player.experienceProgress = progress;
				player.connection.send(new ClientboundSetExperiencePacket(progress, player.totalExperience, s.splitLevel));
			}
		}
	}

	private float xpFraction(ServerPlayer player) {
		ChallengeSettings s = split.settings();
		int n = Math.max(1, split.playerCount());
		int need = Math.max(1, (int) Math.ceil(xpForLevel(s.splitLevel) / (double) n));
		return Math.min(1.0f, s.xpBuckets.getOrDefault(player.getUUID().toString(), 0) / (float) need);
	}

	// ------------------------------------------------------------------
	// Sync
	// ------------------------------------------------------------------

	private void sendStats(MinecraftServer server) {
		int flags = 0;
		if (split.isActive(SplitType.HEARTS)) flags |= SplitStatsPayload.HEARTS;
		if (split.isActive(SplitType.HUNGER)) flags |= SplitStatsPayload.HUNGER;
		if (split.isActive(SplitType.AIR)) flags |= SplitStatsPayload.AIR;
		if (split.isActive(SplitType.LEVEL)) flags |= SplitStatsPayload.LEVEL;

		if (flags == 0) {
			if (statsWereSent) {
				broadcast(server, new SplitStatsPayload(0, 0, List.of()));
				statsWereSent = false;
			}
			return;
		}

		int n = split.playerCount();
		List<SplitStatsPayload.Entry> entries = new ArrayList<>();
		for (ServerPlayer player : split.coloredPlayers(server)) {
			SplitColor color = split.colorOf(player);
			entries.add(new SplitStatsPayload.Entry(color.ordinal(),
					player.getHealth(), player.getMaxHealth(),
					shownFood(player.getFoodData().getFoodLevel(), (flags & SplitStatsPayload.HUNGER) != 0 ? n : 1),
					(flags & SplitStatsPayload.HUNGER) != 0 ? foodShare(n) : 20.0f,
					Math.max(0, player.getAirSupply()), (flags & SplitStatsPayload.AIR) != 0 ? airShare(n) : FULL_AIR,
					xpFraction(player)));
		}
		broadcast(server, new SplitStatsPayload(flags, split.settings().splitLevel, entries));
		statsWereSent = true;
	}

	private static void broadcast(MinecraftServer server, SplitStatsPayload payload) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, SplitStatsPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	void reset() {
		lastFood.clear();
		lastTotalXp.clear();
	}
}
