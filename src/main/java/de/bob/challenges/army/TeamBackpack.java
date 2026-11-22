package de.bob.challenges.army;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import de.bob.challenges.ChallengeManager;

/**
 * Gemeinsamer Team-Rucksack (27 Plätze) für Mob Army Battle: Jeder im Team kann gleichzeitig
 * Items hineinlegen und herausnehmen. Wird in config/challenges.json gespeichert.
 */
public final class TeamBackpack {
	private static final Map<ArmyTeam, SimpleContainer> CONTAINERS = new EnumMap<>(ArmyTeam.class);

	private TeamBackpack() {
	}

	public static void open(ServerPlayer player, ArmyTeam team) {
		SimpleContainer container = container(player.level().getServer(), team);
		SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false) {
			@Override
			public void onRemoved() {
				save(player.level().getServer());
			}
		};
		gui.setTitle(Component.literal("Rucksack " + team.title()));
		for (int i = 0; i < 27; i++) {
			gui.setSlot(i, new Slot(container, i, 0, 0));
		}
		gui.open();
	}

	static SimpleContainer container(MinecraftServer server, ArmyTeam team) {
		return CONTAINERS.computeIfAbsent(team, t -> load(server, t));
	}

	private static SimpleContainer load(MinecraftServer server, ArmyTeam team) {
		SimpleContainer container = new SimpleContainer(27);
		List<String> saved = ArmyBattle.s().armyBackpacks.get(team.name());
		if (saved == null || server == null) {
			return container;
		}
		RegistryOps<com.google.gson.JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
		for (int i = 0; i < saved.size() && i < 27; i++) {
			String json = saved.get(i);
			if (json == null || json.isEmpty()) {
				continue;
			}
			try {
				int slot = i;
				ItemStack.CODEC.parse(ops, JsonParser.parseString(json)).result().ifPresent(stack -> container.setItem(slot, stack));
			} catch (RuntimeException ignored) {
				// kaputter Eintrag – überspringen
			}
		}
		return container;
	}

	public static void save(MinecraftServer server) {
		if (server == null || CONTAINERS.isEmpty()) {
			return;
		}
		RegistryOps<com.google.gson.JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
		for (Map.Entry<ArmyTeam, SimpleContainer> entry : CONTAINERS.entrySet()) {
			List<String> list = new ArrayList<>();
			for (int i = 0; i < 27; i++) {
				ItemStack stack = entry.getValue().getItem(i);
				list.add(stack.isEmpty() ? "" : ItemStack.CODEC.encodeStart(ops, stack).result().map(Object::toString).orElse(""));
			}
			ArmyBattle.s().armyBackpacks.put(entry.getKey().name(), list);
		}
		ChallengeManager.get().save();
	}

	/** Neue Runde: Rucksäcke leeren */
	public static void clear() {
		for (SimpleContainer container : CONTAINERS.values()) {
			container.clearContent();
		}
		ArmyBattle.s().armyBackpacks.clear();
	}
}
