package de.bob.challenges.split;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.bob.challenges.ChallengesMod;

/**
 * Server → Client: Spieler mit Farben, aktive Splits und wer gerade platzieren darf.
 */
public record SplitSyncPayload(List<Entry> players, List<String> activeSplits, String placeTurn, boolean stacks)
		implements CustomPacketPayload {

	public record Entry(String name, int color) {
	}

	public static final CustomPacketPayload.Type<SplitSyncPayload> TYPE = new Type<>(ChallengesMod.id("split_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SplitSyncPayload> CODEC =
			CustomPacketPayload.codec(SplitSyncPayload::write, SplitSyncPayload::new);

	public SplitSyncPayload(RegistryFriendlyByteBuf buf) {
		this(readEntries(buf), readStrings(buf), buf.readUtf(), buf.readBoolean());
	}

	private static List<Entry> readEntries(RegistryFriendlyByteBuf buf) {
		int count = buf.readVarInt();
		List<Entry> entries = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			entries.add(new Entry(buf.readUtf(), buf.readVarInt()));
		}
		return entries;
	}

	private static List<String> readStrings(RegistryFriendlyByteBuf buf) {
		int count = buf.readVarInt();
		List<String> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			list.add(buf.readUtf());
		}
		return list;
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(players.size());
		for (Entry entry : players) {
			buf.writeUtf(entry.name());
			buf.writeVarInt(entry.color());
		}
		buf.writeVarInt(activeSplits.size());
		for (String split : activeSplits) {
			buf.writeUtf(split);
		}
		buf.writeUtf(placeTurn);
		buf.writeBoolean(stacks);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
