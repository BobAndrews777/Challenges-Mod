package de.bob.challenges.split;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.bob.challenges.ChallengesMod;

/**
 * Server → Client: Herzen, Hunger, Luft und XP aller Spieler für die geteilten Leisten.
 *
 * @param flags Bit 1 = Herzen, 2 = Hunger, 4 = Atemluft, 8 = Level
 */
public record SplitStatsPayload(int flags, int sharedLevel, List<Entry> players) implements CustomPacketPayload {
	public static final int HEARTS = 1;
	public static final int HUNGER = 2;
	public static final int AIR = 4;
	public static final int LEVEL = 8;

	/**
	 * Werte eines Spielers. Hunger ist schon in „angezeigte Punkte“ umgerechnet.
	 */
	public record Entry(int color, float health, float maxHealth, float food, float maxFood, int air, int maxAir, float xp) {
	}

	public static final CustomPacketPayload.Type<SplitStatsPayload> TYPE = new Type<>(ChallengesMod.id("split_stats"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SplitStatsPayload> CODEC =
			CustomPacketPayload.codec(SplitStatsPayload::write, SplitStatsPayload::new);

	public SplitStatsPayload(RegistryFriendlyByteBuf buf) {
		this(buf.readVarInt(), buf.readVarInt(), readEntries(buf));
	}

	private static List<Entry> readEntries(RegistryFriendlyByteBuf buf) {
		int count = buf.readVarInt();
		List<Entry> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			list.add(new Entry(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
					buf.readVarInt(), buf.readVarInt(), buf.readFloat()));
		}
		return list;
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(flags);
		buf.writeVarInt(sharedLevel);
		buf.writeVarInt(players.size());
		for (Entry e : players) {
			buf.writeVarInt(e.color());
			buf.writeFloat(e.health());
			buf.writeFloat(e.maxHealth());
			buf.writeFloat(e.food());
			buf.writeFloat(e.maxFood());
			buf.writeVarInt(e.air());
			buf.writeVarInt(e.maxAir());
			buf.writeFloat(e.xp());
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
