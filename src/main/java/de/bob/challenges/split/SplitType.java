package de.bob.challenges.split;

/**
 * Alle Splits aus Bobs Liste. Nur die mit {@code implemented = true} werden schon ausgelost.
 */
public enum SplitType {
	CRAFTING(1, "Craften", "Craften wird nun geteilt!", false),
	LEVEL(2, "Level", "Level werden nun geteilt!", true),
	INVENTORY(3, "Inventar", "Inventar wird nun geteilt!", false),
	HEARTS(4, "Herzen", "Herzen werden nun geteilt!", true),
	MINING(5, "Block abbauen", "Block abbauen wird nun geteilt!", false),
	HUNGER(6, "Hunger", "Hunger wird nun geteilt!", true),
	HOTBAR(7, "Hotbar", "Hotbar wird nun geteilt!", false),
	MOB_DAMAGE(8, "Mob Damage", "Mob Damage wird nun geteilt!", true),
	SNEAK(9, "Sneaken", "Sneaken wird nun geteilt!", false),
	ARMOR(10, "Rüstung", "Rüstung wird nun geteilt!", false),
	LOOT(11, "Item-Loot", "Item-Loot wird nun geteilt!", true),
	SPRINT(12, "Sprinten", "Sprinten wird nun geteilt!", false),
	BEDS(13, "Betten", "Betten werden nun geteilt!", true),
	PLACING(14, "Platzieren", "Platzieren wird nun geteilt!", true),
	MAIN_HAND(15, "Main Hand", "Main Hand wird nun geteilt!", false),
	AIR(16, "Atemluft", "Atemluft wird nun geteilt!", true),
	WEAPONS(17, "Waffen", "Waffen werden nun geteilt!", true),
	PORTALS(18, "Netherportale", "Netherportale werden nun geteilt!", true),
	SKINS(19, "Skins", "Skins werden nun geteilt!", false),
	JUMP(20, "Springen", "Springen wird nun geteilt!", false),
	CHUNKS(21, "Chunks", "Chunks werden nun geteilt!", true),
	STACKS(22, "Stacks", "Stacks werden nun geteilt!", true),
	TOOLS(23, "Werkzeuge", "Werkzeuge werden nun geteilt!", true),
	ENDER_PEARLS(24, "Enderperlen", "Enderperlen werden nun geteilt!", true);

	public final int number;
	public final String displayName;
	public final String announcement;
	public final boolean implemented;

	SplitType(int number, String displayName, String announcement, boolean implemented) {
		this.number = number;
		this.displayName = displayName;
		this.announcement = announcement;
		this.implemented = implemented;
	}

	/** Name für Befehle, z. B. "betten". */
	public String commandName() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	public static SplitType byCommandName(String name) {
		for (SplitType type : values()) {
			if (type.commandName().equalsIgnoreCase(name) || type.displayName.equalsIgnoreCase(name)) {
				return type;
			}
		}
		return null;
	}
}
