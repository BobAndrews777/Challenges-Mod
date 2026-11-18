package de.bob.challenges.client;

import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import de.bob.challenges.ChallengesMod;
import de.bob.challenges.split.SplitColor;
import de.bob.challenges.split.SplitStatsPayload;

/**
 * Ersetzt Herzen, Hunger und Luftblasen durch geteilte Leisten in den Spielerfarben
 * und legt farbige Abschnitte über die XP-Leiste – aber nur, wenn der jeweilige Split aktiv ist.
 */
public final class SplitBarsHud {
	private static final String[] COLOR_NAMES = {"red", "yellow", "blue", "green"};

	private SplitBarsHud() {
	}

	private static Identifier sprite(String name) {
		return ChallengesMod.id("hud/" + name);
	}

	private static String colorName(int color) {
		return COLOR_NAMES[Math.max(0, Math.min(3, color))];
	}

	private static boolean canShowBars() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.gameMode != null && minecraft.gameMode.canHurtPlayer() && minecraft.player != null;
	}

	public static void register() {
		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, original -> (graphics, tracker) -> {
			if (ClientSplitState.has(SplitStatsPayload.HEARTS) && canShowBars()) {
				drawHearts(graphics);
			} else {
				original.extractRenderState(graphics, tracker);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, original -> (graphics, tracker) -> {
			if (ClientSplitState.has(SplitStatsPayload.HUNGER) && canShowBars()) {
				drawFood(graphics);
			} else {
				original.extractRenderState(graphics, tracker);
			}
		});
		HudElementRegistry.replaceElement(VanillaHudElements.AIR_BAR, original -> (graphics, tracker) -> {
			if (ClientSplitState.has(SplitStatsPayload.AIR) && canShowBars()) {
				drawAir(graphics);
			} else {
				original.extractRenderState(graphics, tracker);
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.INFO_BAR, ChallengesMod.id("split_xp"), SplitBarsHud::drawXp);
	}

	/** Herzen von links nach rechts, jeder Abschnitt in seiner Farbe. */
	private static void drawHearts(GuiGraphicsExtractor graphics) {
		List<SplitStatsPayload.Entry> players = ClientSplitState.stats.players();
		int x0 = graphics.guiWidth() / 2 - 91;
		int y = graphics.guiHeight() - HudStatusBarHeightRegistry.getHeight(VanillaHudElements.HEALTH_BAR);
		int index = 0;
		for (SplitStatsPayload.Entry entry : players) {
			String color = colorName(entry.color());
			int hearts = (int) Math.ceil(entry.maxHealth() / 2.0f);
			int health = (int) Math.ceil(entry.health());
			for (int h = 0; h < hearts; h++) {
				int x = x0 + index * 8;
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("heart_empty"), x, y, 9, 9);
				if (health >= h * 2 + 2) {
					graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("heart_full_" + color), x, y, 9, 9);
				} else if (health == h * 2 + 1) {
					graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("heart_half_" + color), x, y, 9, 9);
				}
				index++;
			}
		}
	}

	/** Hunger von rechts nach links, wie in Vanilla. */
	private static void drawFood(GuiGraphicsExtractor graphics) {
		List<SplitStatsPayload.Entry> players = ClientSplitState.stats.players();
		int xRight = graphics.guiWidth() / 2 + 91;
		int y = graphics.guiHeight() - HudStatusBarHeightRegistry.getHeight(VanillaHudElements.FOOD_BAR);
		int index = 0;
		for (SplitStatsPayload.Entry entry : players) {
			String color = colorName(entry.color());
			int icons = (int) Math.ceil(entry.maxFood() / 2.0f);
			int food = Math.round(entry.food());
			for (int f = 0; f < icons; f++) {
				int x = xRight - index * 8 - 9;
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("food_empty"), x, y, 9, 9);
				if (food >= f * 2 + 2) {
					graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("food_full_" + color), x, y, 9, 9);
				} else if (food == f * 2 + 1) {
					graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("food_half_" + color), x, y, 9, 9);
				}
				index++;
			}
		}
	}

	/** Luftblasen – nur sichtbar, wenn bei irgendwem Luft fehlt. */
	private static void drawAir(GuiGraphicsExtractor graphics) {
		List<SplitStatsPayload.Entry> players = ClientSplitState.stats.players();
		boolean anyMissing = false;
		for (SplitStatsPayload.Entry entry : players) {
			if (entry.air() < entry.maxAir()) {
				anyMissing = true;
			}
		}
		if (!anyMissing) {
			return;
		}
		int xRight = graphics.guiWidth() / 2 + 91;
		int y = graphics.guiHeight() - HudStatusBarHeightRegistry.getHeight(VanillaHudElements.AIR_BAR);
		int index = 0;
		for (SplitStatsPayload.Entry entry : players) {
			String color = colorName(entry.color());
			int bubbles = Math.max(1, Math.round(entry.maxAir() / 30.0f));
			int full = (int) Math.ceil(entry.air() / 30.0f);
			for (int b = 0; b < bubbles; b++) {
				int x = xRight - index * 8 - 9;
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(b < full ? "bubble_" + color : "bubble_empty"), x, y, 9, 9);
				index++;
			}
		}
	}

	/** XP-Leiste in Abschnitte teilen: jeder füllt seinen Teil. */
	private static void drawXp(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		if (!ClientSplitState.has(SplitStatsPayload.LEVEL) || !canShowBars()) {
			return;
		}
		List<SplitStatsPayload.Entry> players = ClientSplitState.stats.players();
		int n = players.size();
		int x0 = graphics.guiWidth() / 2 - 91;
		int y = graphics.guiHeight() - 29;
		int width = 182;
		for (int i = 0; i < n; i++) {
			SplitStatsPayload.Entry entry = players.get(i);
			int left = x0 + i * width / n;
			int right = x0 + (i + 1) * width / n;
			graphics.fill(left, y, right, y + 5, 0xFF202020);
			int filled = left + Math.round((right - left - 1) * Math.max(0, Math.min(1, entry.xp())));
			graphics.fill(left, y + 1, filled, y + 4, SplitColor.byIndex(entry.color()).argb);
			if (i > 0) {
				graphics.fill(left, y - 1, left + 1, y + 6, 0xFF000000);
			}
		}
	}
}
