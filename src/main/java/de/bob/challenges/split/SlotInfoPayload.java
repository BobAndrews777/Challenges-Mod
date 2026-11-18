package de.bob.challenges.split;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.bob.challenges.ChallengesMod;

/**
 * Server → Client: Wem gehören die Crafting-Felder (4 + 9 Werte) und wer hat welchen
 * Hotbar-Platz in der Hand (9 Werte). Werte sind SplitColor-Indizes, -1 = niemand.
 */
public record SlotInfoPayload(List<Integer> craftOwners, List<Integer> handHolders) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SlotInfoPayload> TYPE = new Type<>(ChallengesMod.id("slot_info"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SlotInfoPayload> CODEC =
			CustomPacketPayload.codec(SlotInfoPayload::write, SlotInfoPayload::new);

	public SlotInfoPayload(RegistryFriendlyByteBuf buf) {
		this(readInts(buf), readInts(buf));
	}

	private static List<Integer> readInts(RegistryFriendlyByteBuf buf) {
		int count = buf.readVarInt();
		List<Integer> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			list.add(buf.readVarInt() - 1);
		}
		return list;
	}

	private static void writeInts(RegistryFriendlyByteBuf buf, List<Integer> list) {
		buf.writeVarInt(list.size());
		for (int value : list) {
			buf.writeVarInt(value + 1);
		}
	}

	public void write(RegistryFriendlyByteBuf buf) {
		writeInts(buf, craftOwners);
		writeInts(buf, handHolders);
	}

	public static List<Integer> toList(int[] values) {
		List<Integer> list = new ArrayList<>();
		for (int value : values) {
			list.add(value);
		}
		return list;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
