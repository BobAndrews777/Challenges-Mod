package de.bob.challenges.mobs;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.bob.challenges.ChallengesMod;

/** Server → Client: Schwung für den eigenen Spieler (Dash, Hüpfer). Die Bewegung rechnet der Client. */
public record MotionPayload(double x, double y, double z) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<MotionPayload> TYPE = new Type<>(ChallengesMod.id("motion"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MotionPayload> CODEC =
			CustomPacketPayload.codec(MotionPayload::write, MotionPayload::new);

	public MotionPayload(RegistryFriendlyByteBuf buf) {
		this(buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeDouble(x);
		buf.writeDouble(y);
		buf.writeDouble(z);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
