package de.bob.challenges.split;

/**
 * Kleiner gemeinsamer Zustand, den Server und Client kennen müssen
 * (wird von den Mixins benutzt). Auf dem Server setzt ihn der SplitManager,
 * auf dem Client das Sync-Paket vom Server.
 */
public final class SplitFlags {
	public static volatile boolean stacksActive = false;
	public static volatile boolean placingActive = false;
	public static volatile String placeTurnName = "";

	public static final int SPLIT_STACK_SIZE = 16;

	private SplitFlags() {
	}

	public static boolean canPlace(String playerName) {
		String turn = placeTurnName;
		return !placingActive || turn == null || turn.isEmpty() || turn.equals(playerName);
	}

	public static int limitStackSize(int original) {
		return stacksActive && original > SPLIT_STACK_SIZE ? SPLIT_STACK_SIZE : original;
	}
}
