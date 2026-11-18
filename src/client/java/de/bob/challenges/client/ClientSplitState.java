package de.bob.challenges.client;

import java.util.List;

import de.bob.challenges.split.SplitFlags;
import de.bob.challenges.split.SplitSyncPayload;

/** Was der Client zuletzt vom Server über die Split-Challenge erfahren hat. */
public final class ClientSplitState {
	public static volatile List<SplitSyncPayload.Entry> players = List.of();
	public static volatile List<String> activeSplits = List.of();
	public static volatile String placeTurn = "";

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

	public static void clear() {
		players = List.of();
		activeSplits = List.of();
		placeTurn = "";
		SplitFlags.placeTurnName = "";
		SplitFlags.placingActive = false;
		SplitFlags.stacksActive = false;
	}
}
