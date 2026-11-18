package de.bob.challenges.split;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Split 1 „Craften“: Alle craften am selben Feld (2×2 im Inventar, 3×3 an der Werkbank).
 * Jeder darf nur seine farbigen Felder befüllen. Nach jedem Craften werden die Farben neu verteilt.
 */
public final class SharedCrafting {
	/** Ein gemeinsames Feld: Inhalt und Besitzer je Feld (Index in colors()) */
	private static final class Grid {
		final int size;
		final ItemStack[] items;
		int[] owners;

		Grid(int size) {
			this.size = size;
			this.items = new ItemStack[size];
			for (int i = 0; i < size; i++) {
				items[i] = ItemStack.EMPTY;
			}
			this.owners = new int[size];
		}
	}

	private final SplitManager split;
	private final SharedSlots slots;
	private final Grid small = new Grid(4);
	private final Grid big = new Grid(9);
	private final Set<UUID> crafted = new HashSet<>();
	private int lastPlayerCount = -1;

	SharedCrafting(SplitManager split, SharedSlots slots) {
		this.split = split;
		this.slots = slots;
	}

	/** Aus dem ResultSlot-Mixin: dieser Spieler hat gerade etwas gecraftet. */
	public void onCraft(Player player) {
		if (player instanceof ServerPlayer && split.isActive(SplitType.CRAFTING)) {
			crafted.add(player.getUUID());
		}
	}

	/** Felder möglichst gleichmäßig verteilen, in zufälliger Anordnung. */
	private void shuffle(Grid grid, int n) {
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < grid.size; i++) {
			order.add(i % Math.max(1, n));
		}
		Collections.shuffle(order, split.random());
		for (int i = 0; i < grid.size; i++) {
			grid.owners[i] = order.get(i);
		}
	}

	/** Die Grid-Slots im Menü eines Spielers (Slot 0 ist das Ergebnis), oder -1 wenn er dieses Feld gerade nicht hat. */
	private static int firstSlot(ServerPlayer player, Grid grid) {
		if (grid.size == 4) {
			return 1; // InventoryMenu: 0 = Ergebnis, 1–4 = 2×2
		}
		return player.containerMenu instanceof CraftingMenu ? 1 : -1;
	}

	private static AbstractContainerMenu menu(ServerPlayer player, Grid grid) {
		return grid.size == 4 ? player.inventoryMenu : player.containerMenu;
	}

	void tick(MinecraftServer server) {
		if (!split.isActive(SplitType.CRAFTING)) {
			lastPlayerCount = -1;
			crafted.clear();
			return;
		}
		List<SplitColor> colors = slots.colors();
		int n = colors.size();
		if (n == 0) {
			return;
		}
		if (n != lastPlayerCount) {
			shuffle(small, n);
			shuffle(big, n);
			lastPlayerCount = n;
		}
		List<ServerPlayer> players = split.coloredPlayers(server);
		update(small, players, colors);
		update(big, players, colors);
		crafted.clear();
	}

	private void update(Grid grid, List<ServerPlayer> players, List<SplitColor> colors) {
		// Hat jemand gecraftet? Dann gilt sein Feld (nach dem Verbrauch) für alle.
		ServerPlayer crafter = null;
		for (ServerPlayer player : players) {
			if (crafted.contains(player.getUUID()) && firstSlot(player, grid) >= 0
					&& (grid.size == 9) == (player.containerMenu instanceof CraftingMenu)) {
				crafter = player;
			}
		}
		if (crafter != null) {
			AbstractContainerMenu menu = menu(crafter, grid);
			for (int i = 0; i < grid.size; i++) {
				grid.items[i] = Mirrors.unmirror(menu.getSlot(1 + i).getItem());
			}
			shuffle(grid, colors.size());
		} else {
			// Sonst bestimmt jeder Besitzer, was in seinen Feldern liegt
			for (int i = 0; i < grid.size; i++) {
				SplitColor ownerColor = colors.get(Math.min(grid.owners[i], colors.size() - 1));
				ServerPlayer owner = null;
				for (ServerPlayer player : players) {
					if (split.colorOf(player) == ownerColor) {
						owner = player;
					}
				}
				if (owner == null) {
					continue;
				}
				if (firstSlot(owner, grid) < 0) {
					grid.items[i] = ItemStack.EMPTY; // Besitzer hat keine Werkbank offen
					continue;
				}
				ItemStack real = menu(owner, grid).getSlot(1 + i).getItem();
				grid.items[i] = Mirrors.unmirror(real);
			}
		}

		// Allen den gemeinsamen Stand zeigen
		for (ServerPlayer player : players) {
			if (firstSlot(player, grid) < 0) {
				continue;
			}
			SplitColor own = split.colorOf(player);
			AbstractContainerMenu menu = menu(player, grid);
			List<ItemStack> giveBack = new ArrayList<>();
			for (int i = 0; i < grid.size; i++) {
				SplitColor ownerColor = colors.get(Math.min(grid.owners[i], colors.size() - 1));
				ItemStack expected = ownerColor == own
						? grid.items[i].copy()
						: (grid.items[i].isEmpty() ? ItemStack.EMPTY : Mirrors.mirror(grid.items[i], ownerColor));
				ItemStack current = menu.getSlot(1 + i).getItem();
				if (!ItemStack.matches(current, expected)) {
					if (ownerColor != own && !current.isEmpty() && !Mirrors.isMarked(current)) {
						giveBack.add(current.copy()); // in ein fremdes Feld gelegt → zurück
					}
					menu.getSlot(1 + i).set(expected);
				}
			}
			for (ItemStack stack : giveBack) {
				player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
			}
		}
	}

	/** Für die Anzeige: Besitzer-Farbe jedes Feldes (Index in SplitColor), 4 + 9 Werte. */
	public int[] ownerColors() {
		List<SplitColor> colors = slots.colors();
		int[] result = new int[13];
		for (int i = 0; i < 13; i++) {
			Grid grid = i < 4 ? small : big;
			int index = i < 4 ? i : i - 4;
			result[i] = colors.isEmpty() || !split.isActive(SplitType.CRAFTING)
					? -1
					: colors.get(Math.min(grid.owners[index], colors.size() - 1)).ordinal();
		}
		return result;
	}

	void reset() {
		lastPlayerCount = -1;
		crafted.clear();
		for (int i = 0; i < 4; i++) small.items[i] = ItemStack.EMPTY;
		for (int i = 0; i < 9; i++) big.items[i] = ItemStack.EMPTY;
	}
}
