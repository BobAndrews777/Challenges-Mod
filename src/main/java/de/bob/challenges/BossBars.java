package de.bob.challenges;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * Die Leisten oben am Bildschirm: Countdown fürs Zufalls-Item und die aktuelle Force-Aufgabe.
 * Es sind normale Vanilla-Bossbars, daher brauchen die Spieler keine Mod.
 */
public final class BossBars {
	private BossBars() {
	}

	/**
	 * Zeigt eine Leiste an oder blendet sie aus.
	 *
	 * @param name  Text der Leiste, oder null zum Ausblenden
	 * @param value verbleibende Sekunden
	 * @param max   Gesamtzahl der Sekunden
	 */
	public static void show(MinecraftServer server, String id, Component name, int value, int max, BossEvent.BossBarColor color) {
		CustomBossEvents events = server.getCustomBossEvents();
		CustomBossEvent bar = events.get(ChallengesMod.id(id));

		if (name == null) {
			if (bar != null && bar.isVisible()) {
				bar.setVisible(false);
				bar.removeAllPlayers();
			}
			return;
		}

		if (bar == null) {
			bar = events.create(server.overworld().getRandom(), ChallengesMod.id(id), name);
			bar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
		}
		bar.setName(name);
		bar.setColor(color);
		bar.setMax(Math.max(1, max));
		bar.setValue(Math.max(0, Math.min(value, max)));
		bar.setVisible(true);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!bar.getPlayers().contains(player)) {
				bar.addPlayer(player);
			}
		}
	}

	public static void hide(MinecraftServer server, String id) {
		show(server, id, null, 0, 1, BossEvent.BossBarColor.WHITE);
	}
}
