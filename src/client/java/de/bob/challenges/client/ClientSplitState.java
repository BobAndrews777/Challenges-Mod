package de.bob.challenges.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.bob.challenges.split.SplitFlags;
import de.bob.challenges.split.MobDamagePayload;
import de.bob.challenges.split.MovementPayload;
import de.bob.challenges.split.SkinPayload;
import de.bob.challenges.split.SlotInfoPayload;
import de.bob.challenges.split.SplitStatsPayload;
import de.bob.challenges.split.SplitSyncPayload;

/** Was der Client zuletzt vom Server über die Split-Challenge erfahren hat. */
public final class ClientSplitState {
	public static volatile List<SplitSyncPayload.Entry> players = List.of();
	public static volatile List<String> activeSplits = List.of();
	public static volatile String placeTurn = "";
	public static volatile SplitStatsPayload stats = new SplitStatsPayload(0, 0, List.of());
	public static final Map<Integer, MobDamagePayload> mobDamage = new ConcurrentHashMap<>();
	public static volatile List<Integer> craftOwners = List.of();
	public static volatile List<Integer> handHolders = List.of();
	public static volatile Map<String, String> skinSwap = Map.of();
	public static volatile MovementPayload movement = new MovementPayload(0, 0, 0, 0, false);

	private ClientSplitState() {
	}

	public static void update(SplitSyncPayload payload) {
		players = List.copyOf(payload.players());
		activeSplits = List.copyOf(payload.activeSplits());
		placeTurn = payload.placeTurn();
		SplitFlags.placeTurnName = payload.placeTurn();
		SplitFlags.placingActive = !payload.placeTurn().isEmpty();
		SplitFlags.stacksActive = payload.stacks();
	}

	public static void updateStats(SplitStatsPayload payload) {
		stats = payload;
	}

	public static void updateMobDamage(MobDamagePayload payload) {
		if (payload.remove()) {
			mobDamage.remove(payload.entityId());
		} else {
			if (mobDamage.size() > 500) {
				mobDamage.clear();
		craftOwners = List.of();
		handHolders = List.of();
		movement = new MovementPayload(0, 0, 0, 0, false);
		skinSwap = Map.of();
		SplitFlags.edgeProtectionDisabled = false;
			}
			mobDamage.put(payload.entityId(), payload);
		}
	}

	public static void updateSlotInfo(SlotInfoPayload payload) {
		craftOwners = List.copyOf(payload.craftOwners());
		handHolders = List.copyOf(payload.handHolders());
	}

	public static void updateSkins(SkinPayload payload) {
		Map<String, String> map = new java.util.HashMap<>();
		for (int i = 0; i < Math.min(payload.players().size(), payload.skins().size()); i++) {
			map.put(payload.players().get(i), payload.skins().get(i));
		}
		skinSwap = Map.copyOf(map);
	}

	public static void updateMovement(MovementPayload payload) {
		movement = payload;
		SplitFlags.edgeProtectionDisabled = (payload.activeFlags() & MovementPayload.SNEAK) != 0 && !payload.allSneaking();
	}

	public static boolean has(int flag) {
		return (stats.flags() & flag) != 0 && !stats.players().isEmpty();
	}

	public static void clear() {
		stats = new SplitStatsPayload(0, 0, List.of());
		mobDamage.clear();
		players = List.of();
		activeSplits = List.of();
		placeTurn = "";
		SplitFlags.placeTurnName = "";
		SplitFlags.placingActive = false;
		SplitFlags.stacksActive = false;
	}
}
