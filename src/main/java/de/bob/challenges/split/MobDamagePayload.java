package de.bob.challenges.split;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.bob.challenges.ChallengesMod;

/**
 * Server → Client: Wie weit jede Farbe ihren Schadensanteil an einem Mob erfüllt hat.
 * Werte: 0..1 = Fortschritt, -1 = diese Farbe spielt nicht mit. remove = Mob ist weg.
 */
public record MobDamagePayload(int entityId, float red, float yellow, float blue, float green, boolean remove)
		implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<MobDamagePayload> TYPE = new Type<>(ChallengesMod.id("mob_damage"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MobDamagePayload> CODEC =
			CustomPacketPayload.codec(MobDamagePayload::write, MobDamagePayload::new);

	public MobDamagePayload(RegistryFriendlyByteBuf buf) {
		this(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(entityId);
		buf.writeFloat(red);
		buf.writeFloat(yellow);
		buf.writeFloat(blue);
		buf.writeFloat(green);
		buf.writeBoolean(remove);
	}

	public float progress(int colorIndex) {
		return switch (colorIndex) {
			case 0 -> red;
			case 1 -> yellow;
			case 2 -> blue;
			default -> green;
		};
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
