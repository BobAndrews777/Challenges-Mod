package de.bob.challenges.army;

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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;

import de.bob.challenges.ChallengeManager;
import de.bob.challenges.SettingsGui;

/** Einstellungen für Mob Army Battle (aus dem Challenge-Menü): Zeiten, Spawn-Eier, Arena, Teams, Start/Stopp. */
public final class ArmyMenu {
	private final ServerPlayer player;
	private final SimpleGui gui;

	private ArmyMenu(ServerPlayer player) {
		this.player = player;
		this.gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
		this.gui.setTitle(Component.literal("Mob Army Battle"));
	}

	public static void open(ServerPlayer player) {
		ArmyMenu menu = new ArmyMenu(player);
		menu.render();
		menu.gui.open();
	}

	private MinecraftServer server() {
		return player.level().getServer();
	}

	private void render() {
		ArmyBattle army = ArmyBattle.get();
		var s = ArmyBattle.s();
		GuiElementBuilder filler = new GuiElementBuilder(item("gray_stained_glass_pane")).setName(Component.literal(" ")).hideTooltip();
		for (int i = 0; i < 54; i++) {
			gui.setSlot(i, filler.build());
		}

		// Status
		String status = !army.active() ? "Aus" : switch (army.phase()) {
			case ArmyBattle.FARM -> "Farmen – noch " + ArmyBattle.time(s.armyTicksLeft);
			case ArmyBattle.CONFIG -> "Wellen bauen – noch " + ArmyBattle.time(s.armyTicksLeft);
			case ArmyBattle.FIGHT -> "Kampf läuft";
			default -> "Vorbei";
		};
		gui.setSlot(4, new GuiElementBuilder(item("zombie_spawn_egg"))
				.setName(Component.literal("Mob Army Battle").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
				.setLore(List.of(
						Component.literal(status).withStyle(army.active() ? ChatFormatting.GREEN : ChatFormatting.RED),
						Component.empty(),
						gray("1. Farmen: jedes Team in seiner eigenen Welt"),
						gray("2. Wellen bauen: 3 Wellen für das andere Team"),
						gray("3. Kampf: wer zuerst alle Wellen schafft, gewinnt")))
				.hideDefaultTooltip()
				.build());

		// Einstellungen
		number(10, "clock", "Farmzeit", List.of("So lange sammelt ihr Mobs."),
				() -> s.armyFarmMinutes, v -> s.armyFarmMinutes = v, 10, 1, " Minuten");
		number(11, "writable_book", "Zeit zum Wellen-Bauen", List.of("So lange habt ihr für die 3 Wellen."),
				() -> s.armyConfigMinutes, v -> s.armyConfigMinutes = v, 1, 1, " Minuten");
		gui.setSlot(12, new GuiElementBuilder(item("egg"))
				.setName(Component.literal("Spawn-Ei-Chance").withStyle(ChatFormatting.AQUA))
				.setLore(List.of(gray("Chance pro abgebautem Block,"), gray("dass zusätzlich ein Spawn-Ei droppt."), Component.empty(),
						Component.literal("Wert: " + format(s.armyEggChance) + " %").withStyle(ChatFormatting.AQUA),
						Component.literal("Linksklick +0,5 · Rechtsklick −0,5").withStyle(ChatFormatting.YELLOW),
						Component.literal("Shift: ±5").withStyle(ChatFormatting.YELLOW)))
				.hideDefaultTooltip()
				.setCallback((index, type, action, g) -> {
					double step = type.shift ? 5 : 0.5;
					s.armyEggChance = Math.round((s.armyEggChance + (type.isRight ? -step : step)) * 10) / 10.0;
					s.clampValues();
					changed();
				})
				.build());
		String arena = s.armyArena == null ? "" : s.armyArena.trim();
		String arenaName = switch (arena) {
			case "" -> "eingebaute Halle";
			case "desert" -> "Wüste (klein)";
			case "paris" -> "Paris (groß)";
			default -> arena;
		};
		gui.setSlot(13, new GuiElementBuilder(item(arena.isEmpty() ? "stone_bricks" : arena.equals("paris") ? "iron_bars" : "sandstone"))
				.setName(Component.literal("Arena: " + arenaName).withStyle(ChatFormatting.AQUA))
				.setLore(List.of(gray("Klick: Halle → Wüste → Paris"),
						gray("Andere Map: /army arena <name>"), Component.empty(),
						gray("Lapislazuliblock = Mob-Spawn"), gray("Diamantblock = Spieler-Start")))
				.hideDefaultTooltip()
				.setCallback((index, type, action, g) -> {
					s.armyArena = switch (arena) {
						case "" -> "desert";
						case "desert" -> "paris";
						default -> "";
					};
					changed();
				})
				.build());

		gui.setSlot(14, new GuiElementBuilder(item(s.armyGlow ? "glowstone" : "black_dye"))
				.setName(Component.literal("Mobs leuchten: " + (s.armyGlow ? "an" : "aus")).withStyle(s.armyGlow ? ChatFormatting.GREEN : ChatFormatting.RED))
				.setLore(List.of(gray("Wellen-Mobs sind im Kampf"), gray("durch Wände sichtbar."), Component.literal("Klick: umschalten").withStyle(ChatFormatting.YELLOW)))
				.hideDefaultTooltip()
				.setCallback((index, type, action, g) -> {
					s.armyGlow = !s.armyGlow;
					changed();
				})
				.build());

		// Teams
		gui.setSlot(18, new GuiElementBuilder(item("white_banner"))
				.setName(Component.literal("Teams").withStyle(ChatFormatting.GOLD))
				.setLore(List.of(gray("Klick auf einen Spieler:"), gray("Rot → Blau → kein Team"),
						gray("Ohne Team wird beim Start zufällig verteilt.")))
				.hideDefaultTooltip()
				.build());
		int slot = 19;
		for (ServerPlayer online : server().getPlayerList().getPlayers()) {
			if (slot > 34) {
				break;
			}
			if (slot == 27) {
				slot = 28;
			}
			ArmyTeam team = army.teamOf(online);
			String wool = team == ArmyTeam.ROT ? "red_wool" : team == ArmyTeam.BLAU ? "blue_wool" : "white_wool";
			gui.setSlot(slot++, new GuiElementBuilder(item(wool))
					.setName(Component.literal(online.getName().getString()).withStyle(team == null ? ChatFormatting.WHITE : team.color))
					.setLore(List.of(gray(team == null ? "kein Team" : team.title()), Component.literal("Klick: Team wechseln").withStyle(ChatFormatting.YELLOW)))
					.setCallback((index, type, action, g) -> {
						String uuid = online.getUUID().toString();
						s.playerNames.put(uuid, online.getName().getString());
						ArmyTeam current = army.teamOf(online);
						if (current == null) {
							s.armyTeams.put(uuid, ArmyTeam.ROT.name());
						} else if (current == ArmyTeam.ROT) {
							s.armyTeams.put(uuid, ArmyTeam.BLAU.name());
						} else {
							s.armyTeams.remove(uuid);
						}
						changed();
					})
					.build());
		}

		// Steuerung
		gui.setSlot(45, new GuiElementBuilder(item("arrow"))
				.setName(Component.literal("Zurück").withStyle(ChatFormatting.GRAY))
				.setCallback((index, type, action, g) -> SettingsGui.open(player))
				.build());
		gui.setSlot(47, new GuiElementBuilder(item("lime_concrete"))
				.setName(Component.literal(army.active() ? "Neu starten" : "Starten").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
				.setLore(List.of(gray("Inventare werden geleert, alle kommen"), gray("in die Welt ihres Teams.")))
				.setCallback((index, type, action, g) -> {
					gui.close();
					String error = army.start(server());
					if (error != null) {
						player.sendSystemMessage(Component.literal(error).withStyle(ChatFormatting.RED));
					}
				})
				.build());
		if (army.active()) {
			gui.setSlot(49, new GuiElementBuilder(item("spectral_arrow"))
					.setName(Component.literal("Nächste Phase").withStyle(ChatFormatting.YELLOW))
					.setLore(List.of(gray("Springt sofort weiter (Farmen → Wellen → Kampf).")))
					.setCallback((index, type, action, g) -> {
						gui.close();
						army.skip(server());
					})
					.build());
			gui.setSlot(51, new GuiElementBuilder(item("red_concrete"))
					.setName(Component.literal("Beenden").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
					.setCallback((index, type, action, g) -> {
						army.stop(server());
						ChallengeManager.broadcast(server(), Component.literal("Mob Army Battle beendet.").withStyle(ChatFormatting.YELLOW));
						render();
					})
					.build());
		}
	}

	private void number(int slot, String itemId, String name, List<String> description, Supplier<Integer> getter,
			Consumer<Integer> setter, int step, int shiftStep, String unit) {
		List<Component> lore = new ArrayList<>();
		for (String line : description) {
			lore.add(gray(line));
		}
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
					ArmyBattle.s().clampValues();
					changed();
				})
				.build());
	}

	private void changed() {
		ChallengeManager.get().save();
		render();
	}

	private static String format(double value) {
		return value == Math.floor(value) ? String.valueOf((int) value) : String.valueOf(value).replace('.', ',');
	}

	private static Component gray(String text) {
		return Component.literal(text).withStyle(ChatFormatting.GRAY);
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
	}
}
