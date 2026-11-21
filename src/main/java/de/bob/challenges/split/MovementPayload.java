package de.bob.challenges.split;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.bob.challenges.ChallengesMod;

/**
 * Server → Client: Wer drückt gerade Schleichen, Sprinten, Springen (Bit = SplitColor-Index)
 * und welche dieser Splits aktiv sind (Bit 1 = Sneaken, 2 = Sprinten, 4 = Springen).
 */
public record MovementPayload(int activeFlags, int sneakMask, int sprintMask, int jumpMask, boolean allSneaking)
		implements CustomPacketPayload {
	public static final int SNEAK = 1;
	public static final int SPRINT = 2;
	public static final int JUMP = 4;

	public static final CustomPacketPayload.Type<MovementPayload> TYPE = new Type<>(ChallengesMod.id("movement"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MovementPayload> CODEC =
			CustomPacketPayload.codec(MovementPayload::write, MovementPayload::new);

	public MovementPayload(RegistryFriendlyByteBuf buf) {
		this(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(activeFlags);
		buf.writeVarInt(sneakMask);
		buf.writeVarInt(sprintMask);
		buf.writeVarInt(jumpMask);
		buf.writeBoolean(allSneaking);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
