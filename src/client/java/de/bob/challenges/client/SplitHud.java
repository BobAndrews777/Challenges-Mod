package de.bob.challenges.client;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import de.bob.challenges.split.MovementPayload;
import de.bob.challenges.split.SplitColor;
import de.bob.challenges.split.SplitSyncPayload;

/**
 * Übersicht oben rechts: jeder Spielername auf seiner Farbe,
 * darunter die aktiven Splits. Ein Pfeil zeigt, wer gerade platzieren darf.
 */
public final class SplitHud {
	private static final int MARGIN = 4;
	private static final int ROW_HEIGHT = 12;

	private SplitHud() {
	}

	/** Links neben dem Namen: » = sprintet, ▼ = schleicht, ▲ = springt (nur wenn der Split aktiv ist). */
	private static void drawMovementIcons(GuiGraphicsExtractor graphics, Font font, int color, int nameLeft, int y) {
		MovementPayload movement = ClientSplitState.movement;
		int bit = 1 << color;
		StringBuilder icons = new StringBuilder();
		if ((movement.activeFlags() & MovementPayload.JUMP) != 0 && (movement.jumpMask() & bit) != 0) icons.append("▲");
		if ((movement.activeFlags() & MovementPayload.SNEAK) != 0 && (movement.sneakMask() & bit) != 0) icons.append("▼");
		if ((movement.activeFlags() & MovementPayload.SPRINT) != 0 && (movement.sprintMask() & bit) != 0) icons.append("»");
		if (icons.length() > 0) {
			String text = icons.toString();
			graphics.text(font, text, nameLeft - 12 - font.width(text), y + 2, 0xFFFFFFFF);
		}
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		List<SplitSyncPayload.Entry> players = ClientSplitState.players;
		if (players.isEmpty()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int right = graphics.guiWidth() - MARGIN;
		int y = MARGIN;

		for (SplitSyncPayload.Entry entry : players) {
			SplitColor color = SplitColor.byIndex(entry.color());
			String name = entry.name();
			int width = font.width(name) + 8;
			int left = right - width;
			graphics.fill(left, y, right, y + ROW_HEIGHT, color.argb);
			graphics.text(font, name, left + 4, y + 2, color.textArgb);
			drawMovementIcons(graphics, font, entry.color(), left, y);
			if (name.equals(ClientSplitState.placeTurn)) {
				graphics.text(font, "▶", left - 9, y + 2, 0xFFFFFFFF);
			}
			y += ROW_HEIGHT + 2;
		}

		List<String> active = ClientSplitState.activeSplits;
		if (!active.isEmpty()) {
			y += 3;
			String header = "Geteilt:";
			graphics.text(font, header, right - font.width(header), y, 0xFFFFD54F);
			y += 10;
			for (String split : active) {
				graphics.text(font, split, right - font.width(split), y, 0xFFDDDDDD);
				y += 10;
			}
		}
	}
}
