package de.bob.challenges.mobs;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.bob.challenges.ChallengesMod;

/** Server → Client: Hitbox-Größen aller verwandelten Spieler (UUID, Breite, Höhe). */
public record DimsPayload(List<String> players, List<Float> widths, List<Float> heights) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DimsPayload> TYPE = new Type<>(ChallengesMod.id("dims"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DimsPayload> CODEC =
			CustomPacketPayload.codec(DimsPayload::write, DimsPayload::new);

	public DimsPayload(RegistryFriendlyByteBuf buf) {
		this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
		int count = buf.readVarInt();
		for (int i = 0; i < count; i++) {
			players.add(buf.readUtf());
			widths.add(buf.readFloat());
			heights.add(buf.readFloat());
		}
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(players.size());
		for (int i = 0; i < players.size(); i++) {
			buf.writeUtf(players.get(i));
			buf.writeFloat(widths.get(i));
			buf.writeFloat(heights.get(i));
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
