package de.bob.challenges.mobs;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.bob.challenges.ChallengesMod;

/**
 * Server → Client: Welcher Mob man ist und wie lange die Fähigkeiten noch abklingen (in Ticks).
 * kind leer = kein Mob.
 */
public record MobStatePayload(String kind, String ability1, int cooldown1, String ability2, int cooldown2)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<MobStatePayload> TYPE = new Type<>(ChallengesMod.id("mob_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MobStatePayload> CODEC =
			CustomPacketPayload.codec(MobStatePayload::write, MobStatePayload::new);

	public MobStatePayload(RegistryFriendlyByteBuf buf) {
		this(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readUtf(), buf.readVarInt());
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeUtf(kind);
		buf.writeUtf(ability1);
		buf.writeVarInt(cooldown1);
		buf.writeUtf(ability2);
		buf.writeVarInt(cooldown2);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
