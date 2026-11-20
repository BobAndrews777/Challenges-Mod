package de.bob.challenges.mobs;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.bob.challenges.ChallengesMod;

/** Server → alle Clients: welches Mob-Kostüm (Entity-ID) gehört zu welchem Spieler (UUID). */
public record DisguiseMapPayload(List<Integer> ids, List<String> owners) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DisguiseMapPayload> TYPE = new Type<>(ChallengesMod.id("disguise_map"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DisguiseMapPayload> CODEC =
			CustomPacketPayload.codec(DisguiseMapPayload::write, DisguiseMapPayload::read);

	private static DisguiseMapPayload read(RegistryFriendlyByteBuf buf) {
		int size = buf.readVarInt();
		List<Integer> ids = new ArrayList<>();
		List<String> owners = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			ids.add(buf.readVarInt());
			owners.add(buf.readUtf());
		}
		return new DisguiseMapPayload(ids, owners);
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(ids.size());
		for (int i = 0; i < ids.size(); i++) {
			buf.writeVarInt(ids.get(i));
			buf.writeUtf(owners.get(i));
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
