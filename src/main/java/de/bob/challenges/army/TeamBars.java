package de.bob.challenges.army;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import de.bob.challenges.ChallengesMod;

/** Bossleiste, die nur bestimmte Spieler sehen (z. B. nur das eigene Team). */
final class TeamBars {
	private TeamBars() {
	}

	static void show(MinecraftServer server, String id, Component name, int value, int max, BossEvent.BossBarColor color,
			List<ServerPlayer> viewers) {
		CustomBossEvents events = server.getCustomBossEvents();
		CustomBossEvent bar = events.get(ChallengesMod.id(id));
		if (bar == null) {
			bar = events.create(server.overworld().getRandom(), ChallengesMod.id(id), name);
			bar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
		}
		bar.setName(name);
		bar.setColor(color);
		bar.setMax(Math.max(1, max));
		bar.setValue(Math.max(0, Math.min(value, max)));
		bar.setVisible(true);
		for (ServerPlayer player : new ArrayList<>(bar.getPlayers())) {
			if (!viewers.contains(player)) {
				bar.removePlayer(player);
			}
		}
		for (ServerPlayer player : viewers) {
			if (!bar.getPlayers().contains(player)) {
				bar.addPlayer(player);
			}
		}
	}

	static void hide(MinecraftServer server, String id) {
		CustomBossEvent bar = server.getCustomBossEvents().get(ChallengesMod.id(id));
		if (bar != null) {
			bar.setVisible(false);
			bar.removeAllPlayers();
		}
	}
}
