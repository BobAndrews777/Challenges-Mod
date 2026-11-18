package de.bob.challenges.client;

import java.util.List;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

import de.bob.challenges.client.mixin.AbstractContainerScreenAccessor;
import de.bob.challenges.split.SharedLayout;
import de.bob.challenges.split.SplitColor;
import de.bob.challenges.split.SplitSyncPayload;
import de.bob.challenges.split.SplitType;

/**
 * Färbt in Inventar und Werkbank die geteilten Slots in der Farbe ihres Besitzers ein.
 */
public final class SlotColorsScreen {
	private SlotColorsScreen() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof AbstractContainerScreen<?>) {
				ScreenEvents.afterBackground(screen).register(SlotColorsScreen::draw);
			}
		});
	}

	private static void draw(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickProgress) {
		if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
			return;
		}
		List<SplitSyncPayload.Entry> players = ClientSplitState.players;
		int n = players.size();
		if (n == 0) {
			return;
		}
		List<String> active = ClientSplitState.activeSplits;
		boolean inventory = active.contains(SplitType.INVENTORY.displayName);
		boolean hotbar = active.contains(SplitType.HOTBAR.displayName);
		boolean armor = active.contains(SplitType.ARMOR.displayName);
		boolean crafting = active.contains(SplitType.CRAFTING.displayName);
		if (!inventory && !hotbar && !armor && !crafting) {
			return;
		}

		AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) containerScreen;
		int left = accessor.challenges$getLeftPos();
		int top = accessor.challenges$getTopPos();
		AbstractContainerMenu menu = containerScreen.getMenu();
		List<Integer> craftOwners = ClientSplitState.craftOwners;

		for (int i = 0; i < menu.slots.size(); i++) {
			Slot slot = menu.slots.get(i);
			int argb = 0;
			if (slot.container instanceof Inventory) {
				int owner = SharedLayout.owner(slot.getContainerSlot(), n, inventory, hotbar, armor);
				if (owner >= 0 && owner < n) {
					argb = SplitColor.byIndex(players.get(owner).color()).argb;
				} else if (owner == SharedLayout.NOBODY) {
					argb = 0xFF555555;
				}
			} else if (crafting && craftOwners.size() >= 13) {
				int color = -1;
				if (menu instanceof InventoryMenu && i >= 1 && i <= 4) {
					color = craftOwners.get(i - 1);
				} else if (menu instanceof CraftingMenu && i >= 1 && i <= 9) {
					color = craftOwners.get(4 + i - 1);
				}
				if (color >= 0) {
					argb = SplitColor.byIndex(color).argb;
				}
			}
			if (argb != 0) {
				int x = left + slot.x;
				int y = top + slot.y;
				graphics.fill(x, y, x + 16, y + 16, (argb & 0x00FFFFFF) | 0x80000000);
			}
		}
	}
}
