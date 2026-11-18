package de.bob.challenges.client;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import de.bob.challenges.split.SplitColor;
import de.bob.challenges.split.SplitSyncPayload;
import de.bob.challenges.split.SplitType;

/**
 * Split 15 „Main Hand“: Über jedem Hotbar-Platz ein Strich in der Farbe dessen, der ihn gerade benutzt.
 * Ist der eigene Platz besetzt, erscheint ein rotes X.
 */
public final class MainHandHud {
	private MainHandHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!ClientSplitState.activeSplits.contains(SplitType.MAIN_HAND.displayName)) {
			return;
		}
		List<Integer> holders = ClientSplitState.handHolders;
		Minecraft minecraft = Minecraft.getInstance();
		if (holders.size() < 9 || minecraft.player == null) {
			return;
		}
		int ownColor = -1;
		for (SplitSyncPayload.Entry entry : ClientSplitState.players) {
			if (entry.name().equals(minecraft.player.getName().getString())) {
				ownColor = entry.color();
			}
		}
		int x0 = graphics.guiWidth() / 2 - 91;
		int y = graphics.guiHeight() - 22;
		int selected = minecraft.player.getInventory().getSelectedSlot();
		for (int i = 0; i < 9; i++) {
			int holder = holders.get(i);
			if (holder < 0) {
				continue;
			}
			int x = x0 + i * 20 + 2;
			graphics.fill(x, y - 3, x + 18, y - 1, SplitColor.byIndex(holder).argb);
			if (i == selected && holder != ownColor) {
				graphics.text(minecraft.font, "✖", x + 6, y + 6, 0xFFFF4444);
			}
		}
	}
}
