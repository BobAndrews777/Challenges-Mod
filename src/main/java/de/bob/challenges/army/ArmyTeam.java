package de.bob.challenges.army;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.Level;

import de.bob.challenges.ChallengesMod;

/** Die zwei Teams des Mob Army Battle – jedes mit eigener Parallelwelt (gleicher Seed) und eigenem Nether. */
public enum ArmyTeam {
	ROT("Rot", ChatFormatting.RED, BossEvent.BossBarColor.RED, "rot"),
	BLAU("Blau", ChatFormatting.BLUE, BossEvent.BossBarColor.BLUE, "blau");

	public final String displayName;
	public final ChatFormatting color;
	public final BossEvent.BossBarColor barColor;
	public final ResourceKey<Level> world;
	public final ResourceKey<Level> nether;

	ArmyTeam(String displayName, ChatFormatting color, BossEvent.BossBarColor barColor, String id) {
		this.displayName = displayName;
		this.color = color;
		this.barColor = barColor;
		this.world = ResourceKey.create(Registries.DIMENSION, ChallengesMod.id(id + "_welt"));
		this.nether = ResourceKey.create(Registries.DIMENSION, ChallengesMod.id(id + "_nether"));
	}

	public ArmyTeam other() {
		return this == ROT ? BLAU : ROT;
	}

	public String title() {
		return "Team " + displayName;
	}

	public static ArmyTeam parse(String name) {
		if (name == null) {
			return null;
		}
		for (ArmyTeam team : values()) {
			if (team.name().equalsIgnoreCase(name) || team.displayName.equalsIgnoreCase(name)) {
				return team;
			}
		}
		return null;
	}

	/** Zu welcher Team-Welt gehört diese Dimension? (null = keine) */
	public static ArmyTeam ofLevel(ResourceKey<Level> key) {
		for (ArmyTeam team : values()) {
			if (team.world.equals(key) || team.nether.equals(key)) {
				return team;
			}
		}
		return null;
	}
}
