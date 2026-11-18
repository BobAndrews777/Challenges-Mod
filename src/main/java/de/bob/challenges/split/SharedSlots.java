package de.bob.challenges.split;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Splits 3 (Inventar), 7 (Hotbar) und 10 (Rüstung):
 * Alle sehen dieselben Slots mit denselben Items, aber jeder darf nur in seinem farbigen Bereich etwas tun.
 *
 * Funktionsweise: Der Besitzer eines Slots hat das echte Item. Alle anderen bekommen eine
 * wirkungslose Spiegel-Kopie (bzw. eine farbige Glasscheibe, wenn der Slot leer ist).
 */
public final class SharedSlots {
	/** Inventar-Index → Farbe des Besitzers; NOBODY = gesperrt */
	private static final int NOBODY = SharedLayout.NOBODY;
	private static final int OFFHAND = SharedLayout.OFFHAND;

	private final SplitManager split;
	/** Der gemeinsame Stand: Inventar-Index → echtes Item */
	private final Map<Integer, ItemStack> master = new HashMap<>();
	private long tick = 0;

	SharedSlots(SplitManager split) {
		this.split = split;
	}

	/** Die vergebenen Farben in fester Reihenfolge (Rot, Gelb, Blau, Grün). */
	List<SplitColor> colors() {
		List<SplitColor> result = new ArrayList<>();
		for (SplitColor color : SplitColor.values()) {
			if (split.settings().playerColors.containsValue(color.name())) {
				result.add(color);
			}
		}
		return result;
	}

	/** @return Farbindex in {@link #colors()}, NOBODY (gesperrt) oder null (nicht geteilt) */
	Integer owner(int index, int n) {
		int owner = SharedLayout.owner(index, n, split.isActive(SplitType.INVENTORY),
				split.isActive(SplitType.HOTBAR), split.isActive(SplitType.ARMOR));
		return owner == SharedLayout.NOT_SHARED ? null : owner;
	}

	boolean anyActive() {
		return split.isActive(SplitType.INVENTORY) || split.isActive(SplitType.HOTBAR) || split.isActive(SplitType.ARMOR);
	}

	void tick(MinecraftServer server) {
		tick++;
		if (tick % 2 != 0) {
			return;
		}
		List<ServerPlayer> players = split.coloredPlayers(server);
		if (!anyActive()) {
			master.clear();
			if (tick % 20 == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					removeMirrors(player, null, 0);
				}
			}
			return;
		}
		List<SplitColor> colors = colors();
		int n = colors.size();

		// 1. Der Besitzer bestimmt, was im gemeinsamen Slot liegt
		for (int index = 0; index <= OFFHAND; index++) {
			Integer owner = owner(index, n);
			if (owner == null) {
				master.remove(index);
				continue;
			}
			if (owner == NOBODY) {
				master.put(index, ItemStack.EMPTY);
				continue;
			}
			ServerPlayer ownerPlayer = playerWithColor(players, colors.get(owner));
			if (ownerPlayer == null) {
				continue; // offline: letzter bekannter Stand bleibt
			}
			Inventory inventory = ownerPlayer.getInventory();
			ItemStack real = inventory.getItem(index);
			if (Mirrors.isMarked(real)) {
				inventory.setItem(index, ItemStack.EMPTY);
				real = ItemStack.EMPTY;
			}
			master.put(index, real.copy());
		}

		// 2. Alle anderen sehen eine Spiegel-Kopie
		for (ServerPlayer player : players) {
			SplitColor own = split.colorOf(player);
			Inventory inventory = player.getInventory();
			List<ItemStack> giveBack = new ArrayList<>();
			for (int index = 0; index <= OFFHAND; index++) {
				Integer owner = owner(index, n);
				if (owner == null) {
					continue;
				}
				SplitColor ownerColor = owner == NOBODY ? null : colors.get(owner);
				if (ownerColor == own) {
					continue;
				}
				ItemStack shared = master.getOrDefault(index, ItemStack.EMPTY);
				ItemStack expected = shared.isEmpty() ? Mirrors.placeholder(ownerColor) : Mirrors.mirror(shared, ownerColor);
				ItemStack current = inventory.getItem(index);
				if (!ItemStack.matches(current, expected)) {
					if (!current.isEmpty() && !Mirrors.isMarked(current)) {
						giveBack.add(current.copy()); // echtes Item im fremden Bereich → zurück an den Spieler
					}
					inventory.setItem(index, expected);
				}
			}
			removeMirrors(player, colors, n);
			for (ItemStack stack : giveBack) {
				inventory.placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
			}
		}
	}

	/** Spiegel-Kopien außerhalb der geteilten Slots (z. B. auf dem Mauszeiger) entfernen. */
	private void removeMirrors(ServerPlayer player, List<SplitColor> colors, int n) {
		Inventory inventory = player.getInventory();
		for (int index = 0; index <= OFFHAND; index++) {
			Integer owner = colors == null ? null : owner(index, n);
			boolean ownSlot = owner != null && owner != NOBODY && colors.get(owner) == split.colorOf(player);
			if ((owner == null || ownSlot) && Mirrors.isMarked(inventory.getItem(index))) {
				inventory.setItem(index, ItemStack.EMPTY);
			}
		}
		if (Mirrors.isMarked(player.containerMenu.getCarried())) {
			player.containerMenu.setCarried(ItemStack.EMPTY);
		}
	}

	private static ServerPlayer playerWithColor(List<ServerPlayer> players, SplitColor color) {
		for (ServerPlayer player : players) {
			if (SplitManager.get().colorOf(player) == color) {
				return player;
			}
		}
		return null;
	}

	void reset() {
		master.clear();
	}
}
