package de.bob.challenges.extra;

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

import de.bob.challenges.ChallengeManager;
import de.bob.challenges.ChallengeSettings;
import de.bob.challenges.SettingsGui;

/** Menü „Weitere Challenges“ (aus dem Challenge-Menü). */
public final class ExtraMenu {
	private final ServerPlayer player;
	private final SimpleGui gui;

	private ExtraMenu(ServerPlayer player) {
		this.player = player;
		this.gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
		this.gui.setTitle(Component.literal("Weitere Challenges"));
	}

	public static void open(ServerPlayer player) {
		ExtraMenu menu = new ExtraMenu(player);
		menu.render();
		menu.gui.open();
	}

	private void render() {
		ChallengeSettings s = ChallengeManager.get().settings();
		GuiElementBuilder filler = new GuiElementBuilder(item("gray_stained_glass_pane")).setName(Component.literal(" ")).hideTooltip();
		for (int i = 0; i < 54; i++) {
			gui.setSlot(i, filler.build());
		}
		gui.setSlot(4, new GuiElementBuilder(item("nether_star"))
				.setName(Component.literal("Weitere Challenges").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
				.setLore(List.of(gray("Klick auf ein Feld: an/aus"), gray("Gilt, solange der Timer läuft.")))
				.build());

		toggle(10, "grass_block", "Block verlassen = weg", List.of("Verlässt jemand einen Block, verschwindet", "dieser Blocktyp überall."),
				() -> s.extraBlockVanish, v -> s.extraBlockVanish = v);
		toggle(11, "bedrock", "Laufspur-Löcher", List.of("Wo ihr lauft, verschwindet kurz danach", "die ganze Säule bis zum Bedrock."),
				() -> s.extraWalkDecay, v -> s.extraWalkDecay = v);
		toggle(12, "spawner", "Chunk-Mob", List.of("Neuer Chunk = Zufalls-Mob.", "Man ist im Chunk eingesperrt,", "bis das Mob weg ist."),
				() -> s.extraChunkMob, v -> s.extraChunkMob = v);
		toggle(13, "crafting_table", "Craft-Mob", List.of("Wer craftet, lässt bei jedem", "Spieler ein Zufalls-Mob spawnen."),
				() -> s.extraCraftMob, v -> s.extraCraftMob = v);
		toggle(14, "lime_concrete", "Snake", List.of("Jeder hinterlässt eine farbige Spur.", "Kreuzt jemand eine Spur, sterben alle.",
				"Spuren verschwinden nach " + s.snakeSeconds + " Sekunden."), () -> s.extraSnake, v -> s.extraSnake = v);
		toggle(15, "potion", "Kill-Effekt", List.of("Mob töten = zufälliger, permanenter Effekt.", "Jede Stufe zählt als eigener Effekt."),
				() -> s.extraKillEffect, v -> s.extraKillEffect = v);
		toggle(16, "ender_pearl", "Hit-Tausch", List.of("Mob schlagen = Platz tauschen mit", "einem zufälligen Mob in der Welt."),
				() -> s.extraHitSwap, v -> s.extraHitSwap = v);
		toggle(19, "clock", "Zeit-Uhr", List.of("Jeder bekommt eine Uhr: Rechtsklick wechselt", "Normal → Ganz schnell → Ganz langsam."),
				() -> s.extraTickClock, v -> s.extraTickClock = v);
		List<Component> lore = new ArrayList<>();
		lore.add(gray("Wie lange die Snake-Spur bleibt."));
		lore.add(Component.empty());
		lore.add(Component.literal("Wert: " + s.snakeSeconds + " Sekunden").withStyle(ChatFormatting.AQUA));
		lore.add(Component.literal("Linksklick +10 · Rechtsklick −10").withStyle(ChatFormatting.YELLOW));
		gui.setSlot(20, new GuiElementBuilder(item("hopper"))
				.setName(Component.literal("Snake-Spurdauer").withStyle(ChatFormatting.AQUA))
				.setLore(lore)
				.hideDefaultTooltip()
				.setCallback((index, type, action, g) -> {
					s.snakeSeconds += type.isRight ? -10 : 10;
					s.clampValues();
					changed();
				})
				.build());

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
