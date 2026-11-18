package de.bob.challenges.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.Entity;

import de.bob.challenges.ChallengesMod;
import de.bob.challenges.split.MobDamagePayload;

/**
 * Split 8 „Mob Damage“: Über dem Fadenkreuz eine Leiste mit einem Schwert pro Spieler.
 * Dunkel = Anteil noch offen, in Spielerfarbe = Anteil erfüllt.
 */
public final class MobDamageHud {
	private static final String[] COLOR_NAMES = {"red", "yellow", "blue", "green"};

	private MobDamageHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Entity target = Minecraft.getInstance().crosshairPickEntity;
		if (target == null) {
			return;
		}
		MobDamagePayload progress = ClientSplitState.mobDamage.get(target.getId());
		if (progress == null) {
			return;
		}
		int count = 0;
		for (int i = 0; i < 4; i++) {
			if (progress.progress(i) >= 0) {
				count++;
			}
		}
		if (count == 0) {
			return;
		}
		int width = count * 11 + 3;
		int x = graphics.guiWidth() / 2 - width / 2;
		int y = graphics.guiHeight() / 2 - 24;
		graphics.fill(x, y, x + width, y + 13, 0x88000000);
		int slot = 0;
		for (int i = 0; i < 4; i++) {
			float value = progress.progress(i);
			if (value < 0) {
				continue;
			}
			String sprite = value >= 0.999f ? "hud/sword_" + COLOR_NAMES[i] : "hud/sword_dark";
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ChallengesMod.id(sprite), x + 3 + slot * 11, y + 2, 9, 9);
			slot++;
		}
	}
}
