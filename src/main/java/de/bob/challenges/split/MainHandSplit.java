package de.bob.challenges.split;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Split 15 „Main Hand“: Einen Hotbar-Platz kann immer nur einer gleichzeitig benutzen.
 * Wer zuerst draufschaltet, hat ihn – die anderen haben auf diesem Platz nichts in der Hand.
 */
public final class MainHandSplit {
	private final SplitManager split;
	/** Hotbar-Platz → wer ihn gerade hat */
	private final Map<Integer, UUID> holders = new HashMap<>();
	private final int[] holderColors = new int[9];

	MainHandSplit(SplitManager split) {
		this.split = split;
		Arrays.fill(holderColors, -1);
	}

	void tick(MinecraftServer server) {
		if (!split.isActive(SplitType.MAIN_HAND)) {
			holders.clear();
			Arrays.fill(holderColors, -1);
			return;
		}
		List<ServerPlayer> players = split.coloredPlayers(server);
		Map<UUID, Integer> selected = new HashMap<>();
		for (ServerPlayer player : players) {
			selected.put(player.getUUID(), player.getInventory().getSelectedSlot());
		}
		// Wer seinen Platz verlassen hat (oder offline ist), gibt ihn frei
		holders.entrySet().removeIf(e -> selectedSlotHolderCheck(selected, e.getValue(), e.getKey()) == null);
		for (ServerPlayer player : players) {
			int slot = selected.get(player.getUUID());
			holders.putIfAbsent(slot, player.getUUID());
		}
		Arrays.fill(holderColors, -1);
		for (Map.Entry<Integer, UUID> entry : holders.entrySet()) {
			SplitColor color = split.colorOfUuid(entry.getValue().toString());
			if (color != null && entry.getKey() >= 0 && entry.getKey() < 9) {
				holderColors[entry.getKey()] = color.ordinal();
			}
		}
	}

	private static UUID selectedSlotHolderCheck(Map<UUID, Integer> selected, UUID holder, int slot) {
		Integer now = selected.get(holder);
		return now != null && now == slot ? holder : null;
	}

	/** Hat dieser Spieler seinen ausgewählten Platz gerade „in der Hand“? */
	public boolean holdsHand(Player player) {
		if (!split.isActive(SplitType.MAIN_HAND) || !(player instanceof ServerPlayer)) {
			return true;
		}
		UUID holder = holders.get(player.getInventory().getSelectedSlot());
		return holder == null || holder.equals(player.getUUID());
	}

	public void notifyBlocked(ServerPlayer player) {
		UUID holder = holders.get(player.getInventory().getSelectedSlot());
		String name = holder == null ? "jemand anderes" : split.settings().playerNames.getOrDefault(holder.toString(), "jemand anderes");
		player.connection.send(new ClientboundSetActionBarTextPacket(
				Component.literal("Diesen Platz benutzt gerade " + name + " – du hast nichts in der Hand!").withStyle(ChatFormatting.RED)));
	}

	public int[] holderColors() {
		return holderColors.clone();
	}
}
