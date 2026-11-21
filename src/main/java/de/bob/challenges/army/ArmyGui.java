package de.bob.challenges.army;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

/**
 * Wellen-Menü (für das ganze Team gleichzeitig bedienbar):
 * Oben 3 Diamantschwerter = Welle 1–3, darunter die getöteten Mobs als Spawn-Eier (Anzahl = noch frei),
 * unten die gewählte Welle in Spawn-Reihenfolge. Rechtsklick auf ein Ei = +1, Linksklick = −1.
 */
public final class ArmyGui {
	private static final Set<ArmyGui> OPEN = new HashSet<>();

	private final ServerPlayer player;
	private final ArmyTeam team;
	private final SimpleGui gui;
	private int wave;

	private ArmyGui(ServerPlayer player, ArmyTeam team) {
		this.player = player;
		this.team = team;
		ArmyGui self = this;
		this.gui = new SimpleGui(MenuType.GENERIC_9x6, player, false) {
			@Override
			public void onRemoved() {
				OPEN.remove(self);
			}
		};
		this.gui.setTitle(Component.literal("Wellen für " + team.other().title()));
	}

	public static void open(ServerPlayer player) {
		ArmyBattle army = ArmyBattle.get();
		ArmyTeam team = army.teamOf(player);
		if (team == null) {
			return;
		}
		ArmyGui gui = new ArmyGui(player, team);
		gui.render();
		gui.gui.open();
		OPEN.add(gui);
	}

	public static void refreshAll() {
		for (ArmyGui gui : new ArrayList<>(OPEN)) {
			if (gui.gui.isOpen()) {
				gui.render();
			} else {
				OPEN.remove(gui);
			}
		}
	}

	public static void closeAll() {
		for (ArmyGui gui : new ArrayList<>(OPEN)) {
			gui.gui.close();
		}
		OPEN.clear();
	}

	private void changed() {
		refreshAll();
	}

	private void render() {
		ArmyBattle army = ArmyBattle.get();
		boolean config = ArmyBattle.CONFIG.equals(army.phase());
		boolean ready = ArmyBattle.s().armyReady.contains(team.name());
		List<List<String>> waves = army.waves(team);

		for (int i = 0; i < 54; i++) {
			gui.clearSlot(i);
		}
		GuiElementBuilder filler = new GuiElementBuilder(item("gray_stained_glass_pane")).setName(Component.literal(" ")).hideTooltip();
		for (int i = 0; i < 9; i++) {
			gui.setSlot(i, filler.build());
		}
		for (int i = 36; i < 45; i++) {
			gui.setSlot(i, filler.build());
		}

		// Info
		gui.setSlot(0, new GuiElementBuilder(Items.CLOCK)
				.setName(Component.literal(config ? "Noch " + ArmyBattle.time(ArmyBattle.s().armyTicksLeft) : "Mob Army Battle")
						.withStyle(ChatFormatting.GOLD))
				.setLore(List.of(
						gray("Rechtsklick auf ein Ei: +1 zur Welle"),
						gray("Linksklick auf ein Ei: −1 aus der Welle"),
						gray("Unten: Reihenfolge = Spawn-Reihenfolge"),
						gray("Linksklick unten: diesen Eintrag entfernen")))
				.hideDefaultTooltip()
				.build());

		// Die drei Wellen
		for (int w = 0; w < 3; w++) {
			int index = w;
			boolean selected = w == wave;
			List<Component> lore = new ArrayList<>();
			lore.add(gray(waves.get(w).size() + " Mobs"));
			lore.add(Component.literal(selected ? "Ausgewählt" : "Klick: auswählen").withStyle(selected ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
			gui.setSlot(3 + w, new GuiElementBuilder(Items.DIAMOND_SWORD)
					.setName(Component.literal("Welle " + (w + 1)).withStyle(selected ? ChatFormatting.AQUA : ChatFormatting.WHITE, ChatFormatting.BOLD))
					.setCount(Math.max(1, Math.min(64, waves.get(w).size())))
					.setLore(lore)
					.glow(selected)
					.hideDefaultTooltip()
					.setCallback((i, type, action, g) -> {
						wave = index;
						render();
					})
					.build());
		}

		// Fertig-Knopf
		gui.setSlot(8, new GuiElementBuilder(item(ready ? "lime_concrete" : "red_concrete"))
				.setName(Component.literal(ready ? "Fertig! (Klick: doch nicht)" : "Fertig?").withStyle(ready ? ChatFormatting.GREEN : ChatFormatting.RED))
				.setLore(List.of(gray("Sind beide Teams fertig, startet der Kampf sofort.")))
				.setCallback((i, type, action, g) -> {
					MinecraftServer server = player.level().getServer();
					if (server != null) {
						army.toggleReady(server, team);
					}
					changed();
				})
				.build());

		// Pool (Spawn-Eier)
		Map<String, Integer> pool = army.pool(team);
		List<String> mobs = new ArrayList<>(pool.keySet());
		mobs.sort((a, b) -> Integer.compare(pool.get(b), pool.get(a)));
		int slot = 9;
		for (String mob : mobs) {
			if (slot >= 36) {
				break;
			}
			int killed = pool.get(mob);
			int free = army.available(team, mob);
			int inWave = 0;
			for (String entry : waves.get(wave)) {
				if (entry.equals(mob)) {
					inWave++;
				}
			}
			List<Component> lore = new ArrayList<>();
			lore.add(gray("Getötet: " + killed));
			lore.add(Component.literal("Noch frei: " + free).withStyle(free > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
			lore.add(gray("In Welle " + (wave + 1) + ": " + inWave));
			lore.add(Component.empty());
			lore.add(Component.literal("Rechtsklick: +1   Linksklick: −1").withStyle(ChatFormatting.YELLOW));
			gui.setSlot(slot++, new GuiElementBuilder(ArmyBattle.egg(mob))
					.setName(ArmyBattle.mobName(mob).copy().withStyle(ChatFormatting.WHITE))
					.setCount(Math.max(1, Math.min(64, free)))
					.setLore(lore)
					.glow(inWave > 0)
					.setCallback((i, type, action, g) -> {
						if (!config || ready) {
							return;
						}
						boolean ok = type.isRight ? army.addToWave(team, wave, mob) : army.removeFromWave(team, wave, mob);
						if (ok) {
							changed();
						}
					})
					.build());
		}
		if (mobs.isEmpty()) {
			gui.setSlot(22, new GuiElementBuilder(Items.BARRIER)
					.setName(Component.literal("Noch keine Mobs getötet").withStyle(ChatFormatting.RED))
					.build());
		}

		// Reihenfolge der gewählten Welle
		List<String> order = waves.get(wave);
		gui.setSlot(40, new GuiElementBuilder(Items.DIAMOND_SWORD)
				.setName(Component.literal("Welle " + (wave + 1) + " – Reihenfolge ↓").withStyle(ChatFormatting.AQUA))
				.hideDefaultTooltip()
				.build());
		for (int i = 0; i < 9 && i < order.size(); i++) {
			int entry = i;
			boolean last = i == 8 && order.size() > 9;
			gui.setSlot(45 + i, new GuiElementBuilder(ArmyBattle.egg(order.get(i)))
					.setName(Component.literal("#" + (i + 1) + " ").withStyle(ChatFormatting.GRAY)
							.append(ArmyBattle.mobName(order.get(i)).copy().withStyle(ChatFormatting.WHITE)))
					.setLore(last
							? List.of(gray("… und " + (order.size() - 8) + " weitere"), Component.literal("Linksklick: entfernen").withStyle(ChatFormatting.YELLOW))
							: List.of(Component.literal("Linksklick: entfernen").withStyle(ChatFormatting.YELLOW)))
					.setCallback((index, type, action, g) -> {
						if (config && !ready && type.isLeft && army.removeAt(team, wave, entry)) {
							changed();
						}
					})
					.build());
		}
	}

	private static net.minecraft.world.item.Item item(String id) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(id));
	}

	private static Component gray(String text) {
		return Component.literal(text).withStyle(ChatFormatting.GRAY);
	}
}
