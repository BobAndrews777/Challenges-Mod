package de.bob.challenges.split;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.bob.challenges.ChallengesMod;

/** Server → Client: Split 19 „Skins“ – Spieler players[i] trägt den Skin von skins[i]. Leer = kein Tausch. */
public record SkinPayload(List<String> players, List<String> skins) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SkinPayload> TYPE = new Type<>(ChallengesMod.id("skins"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SkinPayload> CODEC =
			CustomPacketPayload.codec(SkinPayload::write, SkinPayload::new);

	public SkinPayload(RegistryFriendlyByteBuf buf) {
		this(readStrings(buf), readStrings(buf));
	}

	private static List<String> readStrings(RegistryFriendlyByteBuf buf) {
		int count = buf.readVarInt();
		List<String> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			list.add(buf.readUtf());
		}
		return list;
	}

	private static void writeStrings(RegistryFriendlyByteBuf buf, List<String> list) {
		buf.writeVarInt(list.size());
		for (String value : list) {
			buf.writeUtf(value);
		}
	}

	public void write(RegistryFriendlyByteBuf buf) {
		writeStrings(buf, players);
		writeStrings(buf, skins);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
