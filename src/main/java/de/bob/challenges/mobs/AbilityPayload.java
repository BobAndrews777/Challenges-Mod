package de.bob.challenges.mobs;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.bob.challenges.ChallengesMod;

/** Client → Server: Fähigkeits-Taste gedrückt (1 = erste Fähigkeit, 2 = zweite). */
public record AbilityPayload(int ability) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<AbilityPayload> TYPE = new Type<>(ChallengesMod.id("ability"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AbilityPayload> CODEC =
			CustomPacketPayload.codec(AbilityPayload::write, AbilityPayload::new);

	public AbilityPayload(RegistryFriendlyByteBuf buf) {
		this(buf.readVarInt());
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(ability);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
