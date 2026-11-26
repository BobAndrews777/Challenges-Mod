package de.bob.challenges;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import de.bob.challenges.mobs.MobChallenge;
import de.bob.challenges.split.SplitManager;

/**
 * Einstellungs-Menü als Truhen-GUI. Funktioniert mit normalen Clients, ohne Client-Mod.
 */
public final class SettingsGui {
	private final ServerPlayer player;
	private final SimpleGui gui;

	private SettingsGui(ServerPlayer player) {
		this.player = player;
		this.gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
		this.gui.setTitle(Component.literal("Challenge-Einstellungen"));
	}

	public static void open(ServerPlayer player) {
		SettingsGui settingsGui = new SettingsGui(player);
		settingsGui.render();
		settingsGui.gui.open();
	}

	private ChallengeManager manager() {
		return ChallengeManager.get();
	}

	private ChallengeSettings s() {
		return manager().settings();
	}

	private MinecraftServer server() {
		return player.level().getServer();
	}

	private void render() {
		GuiElementBuilder filler = new GuiElementBuilder(item("gray_stained_glass_pane"))
				.setName(Component.literal(" "))
				.hideTooltip();
		for (int i = 0; i < 54; i++) {
			gui.setSlot(i, filler.build());
		}

		renderTimer(4);

		label(9, "book", "Grundfunktionen");
		toggle(10, "dragon_egg", "Ziel: Enderdrache",
				List.of("Wer den Enderdrachen besiegt,", "gewinnt die Challenge."),
				() -> s().dragonGoal, v -> s().dragonGoal = v);
		toggle(11, "totem_of_undying", "Ein Tod = Ende",
				List.of("Stirbt ein Spieler, ist die", "Challenge für alle verloren."),
				() -> s().deathEndsChallenge, v -> s().deathEndsChallenge = v);
		toggle(12, "shield", "Pause-Schutz",
				List.of("Solange der Timer pausiert ist,", "gibt es keinen Schaden und", "keinen Blockabbau."),
				() -> s().pauseProtection, v -> s().pauseProtection = v);
		toggle(13, "golden_apple", "UHC",
				List.of("Keine natürliche Regeneration.", "Nur Goldäpfel & Tränke heilen."),
				() -> s().uhc, v -> {
					s().uhc = v;
					manager().applyUhcGameRule(server());
				});
		toggle(14, "red_dye", "Geteilte Herzen",
				List.of("Alle Spieler teilen sich", "dieselben Herzen."),
				() -> s().sharedHearts, v -> s().sharedHearts = v);

		label(18, "ender_eye", "Zufall");
		toggle(19, "grass_block", "Zufällige Block-Drops",
				List.of("Jeder Block droppt ein zufälliges", "Item – aber immer dasselbe."),
				() -> s().randomBlockDrops, v -> s().randomBlockDrops = v);
		toggle(20, "rotten_flesh", "Zufällige Mob-Drops",
				List.of("Jeder Mob droppt zufällige", "Items – aber immer dieselben."),
				() -> s().randomMobDrops, v -> s().randomMobDrops = v);
		toggleWithValue(21, "chest", "Zufälliges Item",
				List.of("Alle paar Sekunden bekommt", "jeder ein zufälliges Item."),
				() -> s().randomItem, v -> s().randomItem = v,
				() -> s().randomItemSeconds, v -> s().randomItemSeconds = v, 10, " Sekunden");

		label(27, "iron_sword", "Schaden");
		toggleWithValue(28, "rabbit_foot", "Schaden beim Springen",
				List.of("Jeder Sprung kostet Herzen."),
				() -> s().jumpDamage, v -> s().jumpDamage = v,
				() -> s().jumpDamageHearts, v -> s().jumpDamageHearts = v, 1, " Herz(en)");
		toggle(29, "leather_boots", "Tod beim Schleichen",
				List.of("Wer schleicht, stirbt sofort."),
				() -> s().sneakDeath, v -> s().sneakDeath = v);

		label(36, "compass", "Force-Challenges");
		toggle(37, "ladder", "Force Höhe",
				List.of("Jeder muss rechtzeitig einmal", "auf einer bestimmten Höhe stehen."),
				() -> s().forceHeight, v -> s().forceHeight = v);
		toggle(38, "water_bucket", "Force Wasser-MLG",
				List.of("Ab und zu geht's hoch hinaus –", "mit Wassereimer (Nether: Zwirbelranken)."),
				() -> s().forceWaterMlg, v -> s().forceWaterMlg = v);
		toggle(39, "slime_block", "Force Random-MLG",
				List.of("Wie Wasser-MLG, aber mit zufälliger", "MLG-Art (Netz, Slime, Heu, Boot …)."),
				() -> s().forceRandomMlg, v -> s().forceRandomMlg = v);
		boolean battleOn = ForceBattle.get().enabled();
		gui.setSlot(47, new GuiElementBuilder(item("golden_sword"))
				.setName(Component.literal("Force Battle").withStyle(ChatFormatting.LIGHT_PURPLE))
				.setLore(List.of(status(battleOn), Component.empty(),
						gray("Jeder bekommt eigene Aufgaben (Item, Mob,"), gray("Block, Biom, Fortschritt). Wer nach Ablauf"),
						gray("der Zeit mehr geschafft hat, gewinnt."), Component.empty(),
						Component.literal("Klick: Einstellungen").withStyle(ChatFormatting.YELLOW)))
				.glow(battleOn)
				.hideDefaultTooltip()
				.setCallback((index, type, action, g) -> ForceBattleMenu.open(player))
				.build());
		value(41, "clock", "Force-Zeitlimit",
				List.of("So viel Zeit habt ihr", "für jede Force-Aufgabe."),
				() -> s().forceMinutes, v -> s().forceMinutes = v, 1, " Minute(n)");

		toggle(42, "zombie_spawn_egg", "Mob-Challenge",
				List.of("Beim Timer-Start wird jeder", "in einen zufälligen Mob verwandelt.", "Stirbt einer, sterben alle."),
				() -> s().mobMode, v -> {
					s().mobMode = v;
					if (v && manager().isRunning()) {
						MobChallenge.get().onTimerStart(server());
					}
				});
		toggle(43, "shears", "Split-Challenge",
				List.of("Nach und nach wird alles", "zwischen euch aufgeteilt.", "Stirbt einer, sterben alle."),
				() -> s().splitMode, v -> {
					s().splitMode = v;
					if (v && manager().isRunning()) {
						SplitManager.get().onTimerStart(server());
					} else {
						SplitManager.get().sync(server());
					}
				});
		renderSplitWindow(44);

		gui.setSlot(22, new GuiElementBuilder(item("nether_star"))
				.setName(Component.literal("Weitere Challenges").withStyle(ChatFormatting.GOLD))
				.setLore(List.of(gray("Block verlassen, Laufspur, Chunk-Mob, Craft-Mob,"), gray("Snake, Kill-Effekt, Hit-Tausch, Zeit-Uhr,"),
						gray("Force Höhe, Wasser-MLG, Random-MLG"), Component.empty(),
						Component.literal("Klick: öffnen").withStyle(ChatFormatting.YELLOW)))
				.hideDefaultTooltip()
				.setCallback((index, type, action, g) -> de.bob.challenges.extra.ExtraMenu.open(player))
				.build());
		boolean armyOn = de.bob.challenges.army.ArmyBattle.get().active();
		gui.setSlot(40, new GuiElementBuilder(item("diamond_sword"))
				.setName(Component.literal("Mob Army Battle").withStyle(ChatFormatting.GOLD))
				.setLore(List.of(status(armyOn), Component.empty(),
						gray("2 Teams farmen Mobs und schicken"), gray("sie sich als Wellen gegenseitig."),
						Component.empty(), Component.literal("Klick: Einstellungen & Start").withStyle(ChatFormatting.YELLOW)))
				.glow(armyOn)
				.hideDefaultTooltip()
				.setCallback((index, type, action, g) -> de.bob.challenges.army.ArmyMenu.open(player))
				.build());

		gui.setSlot(45, new GuiElementBuilder(Items.BARRIER)
				.setName(Component.literal("Schließen").withStyle(ChatFormatting.RED))
				.setCallback((index, type, action, g) -> gui.close())
				.build());

		gui.setSlot(49, new GuiElementBuilder(item("water_bucket"))
				.setName(Component.literal("Timer zurücksetzen").withStyle(ChatFormatting.YELLOW))
				.setLore(List.of(
						gray("Setzt den Timer auf 0 und holt"),
						gray("alle aus dem Zuschauermodus."),
						gray("Die Welt bleibt erhalten."),
						Component.empty(),
						Component.literal("Shift-Klick zum Bestätigen").withStyle(ChatFormatting.GOLD)))
				.setCallback((index, type, action, g) -> {
					if (type.shift) {
						manager().resetTimer(server());
						render();
					}
				})
				.build());

		gui.setSlot(53, new GuiElementBuilder(item("tnt"))
				.setName(Component.literal("Neue Welt").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
				.setLore(List.of(
						gray("Löscht die Welt und startet"),
						gray("den Server neu."),
						Component.empty(),
						Component.literal("Nutze dafür: /challenge reset").withStyle(ChatFormatting.GOLD)))
				.build());
	}

	private void renderTimer(int slot) {
		boolean running = manager().isRunning();
		boolean ended = manager().isEnded();
		List<Component> lore = new ArrayList<>();
		lore.add(gray("Zeit: " + ChallengeManager.formatTime(s().timerTicks)));
		lore.add(Component.empty());
		if (ended) {
			lore.add(Component.literal("Challenge beendet").withStyle(ChatFormatting.RED));
			lore.add(gray("Zurücksetzen unten im Menü"));
		} else if (running) {
			lore.add(Component.literal("Klick: Timer pausieren").withStyle(ChatFormatting.YELLOW));
		} else {
			lore.add(Component.literal("Klick: Timer starten").withStyle(ChatFormatting.GREEN));
		}
		gui.setSlot(slot, new GuiElementBuilder(item("clock"))
				.setName(Component.literal(running ? "Timer läuft" : ended ? "Timer beendet" : "Timer pausiert")
						.withStyle(running ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD))
				.setLore(lore)
				.glow(running)
				.setCallback((index, type, action, g) -> {
					if (manager().isEnded()) {
						return;
					}
					if (manager().isRunning()) {
						manager().pause(server());
					} else {
						manager().start(server());
					}
					render();
				})
				.build());
	}

	private void renderSplitWindow(int slot) {
		List<Component> lore = new ArrayList<>();
		lore.add(gray("Nach so vielen Minuten kommt"));
		lore.add(gray("der nächste Split (zufällig)."));
		lore.add(Component.empty());
		lore.add(Component.literal("Frühestens: " + s().splitMinMinutes + " Min").withStyle(ChatFormatting.AQUA));
		lore.add(Component.literal("Spätestens: " + s().splitMaxMinutes + " Min").withStyle(ChatFormatting.AQUA));
		lore.add(Component.literal("Joker-Chance: " + s().jokerChancePercent + " %").withStyle(ChatFormatting.AQUA));
		lore.add(Component.empty());
		lore.add(Component.literal("Linksklick: frühestens +1").withStyle(ChatFormatting.YELLOW));
		lore.add(Component.literal("Rechtsklick: frühestens −1").withStyle(ChatFormatting.YELLOW));
		lore.add(Component.literal("Shift-Links: spätestens +1").withStyle(ChatFormatting.YELLOW));
		lore.add(Component.literal("Shift-Rechts: spätestens −1").withStyle(ChatFormatting.YELLOW));
		lore.add(gray("Joker-Chance: /split jokerchance <prozent>"));

		gui.setSlot(slot, new GuiElementBuilder(item("clock"))
				.setName(Component.literal("Split-Zeitfenster").withStyle(ChatFormatting.AQUA))
				.setLore(lore)
				.hideDefaultTooltip()
				.setCallback((index, type, action, g) -> {
					int delta = type.isRight ? -1 : 1;
					if (type.shift) {
						s().splitMaxMinutes = Math.max(s().splitMinMinutes, s().splitMaxMinutes + delta);
					} else {
						s().splitMinMinutes = Math.max(1, s().splitMinMinutes + delta);
						s().splitMaxMinutes = Math.max(s().splitMinMinutes, s().splitMaxMinutes);
					}
					s().clampValues();
					changed("Split-Zeitfenster: " + s().splitMinMinutes + "–" + s().splitMaxMinutes + " Minuten");
				})
				.build());
	}

	private void label(int slot, String itemId, String title) {
		gui.setSlot(slot, new GuiElementBuilder(item(itemId))
				.setName(Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
				.hideDefaultTooltip()
				.build());
	}

	private void toggle(int slot, String itemId, String name, List<String> description,
						BooleanSupplier getter, Consumer<Boolean> setter) {
		boolean on = getter.getAsBoolean();
		List<Component> lore = new ArrayList<>();
		for (String line : description) {
			lore.add(gray(line));
		}
		lore.add(Component.empty());
		lore.add(status(on));
		lore.add(Component.literal("Klick: an/aus").withStyle(ChatFormatting.YELLOW));

		gui.setSlot(slot, new GuiElementBuilder(item(itemId))
				.setName(Component.literal(name).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED))
				.setLore(lore)
				.hideDefaultTooltip()
				.glow(on)
				.setCallback((index, type, action, g) -> {
					boolean newValue = !getter.getAsBoolean();
					setter.accept(newValue);
					changed(name + ": " + (newValue ? "AN" : "AUS"));
				})
				.build());
	}

	private void toggleWithValue(int slot, String itemId, String name, List<String> description,
								 BooleanSupplier getter, Consumer<Boolean> setter,
								 IntSupplier valueGetter, Consumer<Integer> valueSetter, int step, String unit) {
		boolean on = getter.getAsBoolean();
		List<Component> lore = new ArrayList<>();
		for (String line : description) {
			lore.add(gray(line));
		}
		lore.add(Component.empty());
		lore.add(status(on));
		lore.add(Component.literal("Wert: " + valueGetter.getAsInt() + unit).withStyle(ChatFormatting.AQUA));
		lore.add(Component.literal("Linksklick: an/aus").withStyle(ChatFormatting.YELLOW));
		lore.add(Component.literal("Rechtsklick: Wert +").withStyle(ChatFormatting.YELLOW));
		lore.add(Component.literal("Shift-Rechtsklick: Wert −").withStyle(ChatFormatting.YELLOW));

		gui.setSlot(slot, new GuiElementBuilder(item(itemId))
				.setName(Component.literal(name).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED))
				.setLore(lore)
				.hideDefaultTooltip()
				.glow(on)
				.setCallback((index, type, action, g) -> {
					if (type.isRight) {
						valueSetter.accept(valueGetter.getAsInt() + (type.shift ? -step : step));
						s().clampValues();
						changed(name + ": " + valueGetter.getAsInt() + unit);
					} else if (type.isLeft) {
						boolean newValue = !getter.getAsBoolean();
						setter.accept(newValue);
						changed(name + ": " + (newValue ? "AN" : "AUS"));
					}
				})
				.build());
	}

	private void value(int slot, String itemId, String name, List<String> description,
					   IntSupplier valueGetter, Consumer<Integer> valueSetter, int step, String unit) {
		List<Component> lore = new ArrayList<>();
		for (String line : description) {
			lore.add(gray(line));
		}
		lore.add(Component.empty());
		lore.add(Component.literal("Wert: " + valueGetter.getAsInt() + unit).withStyle(ChatFormatting.AQUA));
		lore.add(Component.literal("Linksklick: Wert +").withStyle(ChatFormatting.YELLOW));
		lore.add(Component.literal("Rechtsklick: Wert −").withStyle(ChatFormatting.YELLOW));

		gui.setSlot(slot, new GuiElementBuilder(item(itemId))
				.setName(Component.literal(name).withStyle(ChatFormatting.AQUA))
				.setLore(lore)
				.hideDefaultTooltip()
				.setCallback((index, type, action, g) -> {
					if (type.isLeft || type.isRight) {
						valueSetter.accept(valueGetter.getAsInt() + (type.isRight ? -step : step));
						s().clampValues();
						changed(name + ": " + valueGetter.getAsInt() + unit);
					}
				})
				.build());
	}

	private void changed(String message) {
		manager().save();
		ChallengeManager.broadcast(server(), Component.literal(message).withStyle(ChatFormatting.WHITE));
		render();
	}

	private static Component status(boolean on) {
		return on
				? Component.literal("● Aktiv").withStyle(ChatFormatting.GREEN)
				: Component.literal("● Inaktiv").withStyle(ChatFormatting.RED);
	}

	private static Component gray(String text) {
		return Component.literal(text).withStyle(ChatFormatting.GRAY);
	}

	private static Item item(String id) {
		Identifier key = Identifier.withDefaultNamespace(id);
		return BuiltInRegistries.ITEM.containsKey(key) ? BuiltInRegistries.ITEM.getValue(key) : Items.BARRIER;
	}
}
