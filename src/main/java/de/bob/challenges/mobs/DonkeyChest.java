package de.bob.challenges.mobs;

import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

/**
 * Die Kiste des Esels: 27 Plätze, nur Mitspieler können sie öffnen (schleichen + Rechtsklick).
 * Gespeichert wird sie in der Endertruhe des Esel-Spielers – so bleibt sie auch nach einem Neustart erhalten.
 */
public final class DonkeyChest {
	private DonkeyChest() {
	}

	public static void open(ServerPlayer viewer, ServerPlayer donkey) {
		SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, viewer, false);
		gui.setTitle(Component.literal("Kiste von " + donkey.getName().getString()));
		for (int i = 0; i < 27; i++) {
			gui.setSlot(i, new Slot(donkey.getEnderChestInventory(), i, 0, 0));
		}
		gui.open();
		MobChallenge.playSound(donkey, "minecraft:entity.donkey.chest", 1.0f);
	}
}
