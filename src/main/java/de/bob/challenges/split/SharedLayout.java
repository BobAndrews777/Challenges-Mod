package de.bob.challenges.split;

/**
 * Welche Farbe besitzt welchen Inventar-Slot? Wird von Server (SharedSlots) und Client
 * (farbige Slot-Hintergründe) gleich berechnet.
 */
public final class SharedLayout {
	public static final int NOT_SHARED = -2;
	public static final int NOBODY = -1;
	public static final int OFFHAND = 40;

	private SharedLayout() {
	}

	/**
	 * @param index Inventar-Index (0–8 Hotbar, 9–35 Inventar, 36–39 Rüstung, 40 Zweithand)
	 * @param n     Anzahl Spieler mit Farbe
	 * @return Position in der Farbliste (0 = erste vergebene Farbe), NOBODY oder NOT_SHARED
	 */
	public static int owner(int index, int n, boolean inventory, boolean hotbar, boolean armor) {
		if (n <= 0) {
			return NOT_SHARED;
		}
		if (index >= 9 && index <= 35 && inventory) {
			int per = switch (n) {
				case 1 -> 27;
				case 2 -> 12;
				case 3 -> 9;
				default -> 6;
			};
			int pos = index - 9;
			return pos < per * n ? pos / per : NOBODY;
		}
		if ((index >= 0 && index <= 8 || index == OFFHAND) && hotbar) {
			return switch (n) {
				case 1 -> 0;
				case 2 -> index == OFFHAND ? 1 : (index <= 4 ? 0 : 1);
				case 3 -> index == OFFHAND ? 0 : index / 3;
				default -> index == OFFHAND ? 0 : (index == 8 ? NOBODY : index / 2);
			};
		}
		if (index >= 36 && index <= 39 && armor) {
			int piece = 39 - index; // 0 Helm, 1 Brust, 2 Hose, 3 Schuhe
			return switch (n) {
				case 1 -> 0;
				case 2 -> piece <= 1 ? 0 : 1;
				case 3 -> piece <= 1 ? 0 : piece - 1;
				default -> piece;
			};
		}
		return NOT_SHARED;
	}
}
