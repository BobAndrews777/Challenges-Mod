package de.bob.challenges.mobs;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.bob.challenges.ChallengesMod;

/** Server → Client: Glücksrad-Walze vor einer Verwandlung (wer, welcher Mob, wie lange in Ticks). */
public record ReelPayload(String player, String kind, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ReelPayload> TYPE = new Type<>(ChallengesMod.id("reel"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ReelPayload> CODEC =
			CustomPacketPayload.codec(ReelPayload::write, ReelPayload::new);

	public ReelPayload(RegistryFriendlyByteBuf buf) {
		this(buf.readUtf(), buf.readUtf(), buf.readVarInt());
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeUtf(player);
		buf.writeUtf(kind);
		buf.writeVarInt(ticks);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
