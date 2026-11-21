package de.bob.challenges.mobs;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.bob.challenges.ChallengesMod;

/** Server → Client: Entity-ID des eigenen Mob-Kostüms. */
public record OwnDisguisePayload(int entityId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OwnDisguisePayload> TYPE = new Type<>(ChallengesMod.id("own_disguise"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OwnDisguisePayload> CODEC =
			CustomPacketPayload.codec(OwnDisguisePayload::write, OwnDisguisePayload::new);

	public OwnDisguisePayload(RegistryFriendlyByteBuf buf) {
		this(buf.readVarInt());
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(entityId);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
