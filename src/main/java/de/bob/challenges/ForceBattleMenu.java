package de.bob.challenges;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;

/** Einstellungen für das Force Battle. */
public final class ForceBattleMenu {
	private final ServerPlayer player;
	private final SimpleGui gui;

	private ForceBattleMenu(ServerPlayer player) {
		this.player = player;
		this.gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
		this.gui.setTitle(Component.literal("Force Battle"));
	}

	public static void open(ServerPlayer player) {
		ForceBattleMenu menu = new ForceBattleMenu(player);
		menu.render();
		menu.gui.open();
	}

	private void render() {
		ChallengeSettings s = ChallengeManager.get().settings();
		GuiElementBuilder filler = new GuiElementBuilder(item("gray_stained_glass_pane")).setName(Component.literal(" ")).hideTooltip();
		for (int i = 0; i < 54; i++) {
			gui.setSlot(i, filler.build());
		}
		toggle(4, "golden_sword", "Force Battle", List.of("Startet mit dem Timer.", "Jeder bekommt eigene Aufgaben – abwechselnd",
				"aus den aktivierten Arten. Geschafft = 1 Punkt."), () -> s.forceBattle, v -> s.forceBattle = v);

		toggle(19, "item_frame", "Force Item", List.of("Besorge ein bestimmtes Item."), () -> s.battleItem, v -> s.battleItem = v);
		toggle(20, "zombie_head", "Force Mob", List.of("Töte einen bestimmten Mob."), () -> s.battleMob, v -> s.battleMob = v);
		toggle(21, "grass_block", "Force Block", List.of("Stell dich auf einen bestimmten Block."), () -> s.battleBlock, v -> s.battleBlock = v);
		toggle(22, "oak_sapling", "Force Biom", List.of("Finde ein bestimmtes Biom."), () -> s.battleBiome, v -> s.battleBiome = v);
		toggle(23, "knowledge_book", "Force Fortschritt", List.of("Erreiche einen bestimmten Fortschritt", "(Achievement)."),
				() -> s.battleAdvancement, v -> s.battleAdvancement = v);

		number(30, "clock", "Spielzeit", "Wie lange das Battle dauert.", () -> s.battleMinutes, v -> s.battleMinutes = v, 10, 5, " Minuten");
		number(32, "barrier", "Joker pro Spieler", "Überspringen eine Aufgabe (zählt als Punkt).", () -> s.battleJokers, v -> s.battleJokers = v, 1, 1, "");

		gui.setSlot(45, new GuiElementBuilder(item("arrow"))
				.setName(Component.literal("Zurück").withStyle(ChatFormatting.GRAY))
				.setCallback((index, type, action, g) -> SettingsGui.open(player))
				.build());
	}

	private void toggle(int slot, String itemId, String name, List<String> description, Supplier<Boolean> getter, Consumer<Boolean> setter) {
		boolean on = getter.get();
		List<Component> lore = new ArrayList<>();
		lore.add(Component.literal(on ? "AN" : "AUS").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD));
		for (String line : description) {
			lore.add(gray(line));
		}
		gui.setSlot(slot, new GuiElementBuilder(item(itemId))
				.setName(Component.literal(name).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.WHITE))
				.setLore(lore)
				.glow(on)
				.hideDefaultTooltip()
				.setCallback((index, type, action, g) -> {
					setter.accept(!getter.get());
					changed();
				})
				.build());
	}

	private void number(int slot, String itemId, String name, String description, Supplier<Integer> getter, Consumer<Integer> setter,
			int step, int shiftStep, String unit) {
		List<Component> lore = new ArrayList<>();
		lore.add(gray(description));
		lore.add(Component.empty());
		lore.add(Component.literal("Wert: " + getter.get() + unit).withStyle(ChatFormatting.AQUA));
		lore.add(Component.literal("Linksklick +" + step + " · Rechtsklick −" + step).withStyle(ChatFormatting.YELLOW));
		if (shiftStep != step) {
			lore.add(Component.literal("Shift: ±" + shiftStep).withStyle(ChatFormatting.YELLOW));
		}
		gui.setSlot(slot, new GuiElementBuilder(item(itemId))
				.setName(Component.literal(name).withStyle(ChatFormatting.AQUA))
				.setLore(lore)
				.hideDefaultTooltip()
				.setCallback((index, type, action, g) -> {
					int amount = type.shift ? shiftStep : step;
					setter.accept(getter.get() + (type.isRight ? -amount : amount));
					ChallengeManager.get().settings().clampValues();
					changed();
				})
				.build());
	}

	private void changed() {
		ChallengeManager.get().save();
		render();
	}

	private static Component gray(String text) {
		return Component.literal(text).withStyle(ChatFormatting.GRAY);
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
	}
}
