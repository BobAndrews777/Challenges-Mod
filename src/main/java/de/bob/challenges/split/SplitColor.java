package de.bob.challenges.split;

import net.minecraft.ChatFormatting;

/** Die vier Spielerfarben. Die Reihenfolge ist auch die Reihenfolge beim Platzieren. */
public enum SplitColor {
	RED("Rot", "red", ChatFormatting.RED, 0xFFD32F2F, 0xFFFFFFFF),
	YELLOW("Gelb", "yellow", ChatFormatting.YELLOW, 0xFFFBC02D, 0xFF202020),
	BLUE("Blau", "blue", ChatFormatting.BLUE, 0xFF1E5BD8, 0xFFFFFFFF),
	GREEN("Grün", "green", ChatFormatting.GREEN, 0xFF2E9E3E, 0xFFFFFFFF);

	public final String germanName;
	/** Farbname, den der /team-Befehl versteht. */
	public final String teamColor;
	public final ChatFormatting chat;
	/** Hintergrundfarbe in der Übersicht (ARGB). */
	public final int argb;
	/** Textfarbe auf dem Hintergrund (ARGB). */
	public final int textArgb;

	SplitColor(String germanName, String teamColor, ChatFormatting chat, int argb, int textArgb) {
		this.germanName = germanName;
		this.teamColor = teamColor;
		this.chat = chat;
		this.argb = argb;
		this.textArgb = textArgb;
	}

	public String teamName() {
		return "split_" + teamColor;
	}

	public static SplitColor byIndex(int index) {
		SplitColor[] values = values();
		return index >= 0 && index < values.length ? values[index] : RED;
	}
}
