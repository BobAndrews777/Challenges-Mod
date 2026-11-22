package de.bob.challenges.army;

import java.util.Map;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

import de.bob.challenges.ChallengeManager;
import de.bob.challenges.ChallengesMod;

/** /army … (OP) und /wellen (alle) */
public final class ArmyCommands {
	private ArmyCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("army")
				.requires(PermissionPredicates.require(ChallengesMod.id("admin"), PermissionLevel.GAMEMASTERS))
				.executes(ArmyCommands::status)
				.then(Commands.literal("status").executes(ArmyCommands::status))
				.then(Commands.literal("start").executes(ArmyCommands::start))
				.then(Commands.literal("stop").executes(ctx -> {
					ArmyBattle.get().stop(ctx.getSource().getServer());
					ok(ctx, "Mob Army Battle beendet.");
					return 1;
				}))
				.then(Commands.literal("weiter").executes(ctx -> {
					ArmyBattle.get().skip(ctx.getSource().getServer());
					return 1;
				}))
				.then(Commands.literal("team")
						.then(Commands.argument("spieler", EntityArgument.player())
								.then(Commands.argument("team", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(new String[] {"rot", "blau", "keins"}, builder))
										.executes(ArmyCommands::setTeam))))
				.then(Commands.literal("zeit")
						.then(Commands.argument("minuten", IntegerArgumentType.integer(1, 600)).executes(ctx -> {
							ArmyBattle.s().armyFarmMinutes = IntegerArgumentType.getInteger(ctx, "minuten");
							ChallengeManager.get().save();
							ok(ctx, "Farmzeit: " + ArmyBattle.s().armyFarmMinutes + " Minuten (gilt ab dem nächsten Start)");
							return 1;
						})))
				.then(Commands.literal("wellenzeit")
						.then(Commands.argument("minuten", IntegerArgumentType.integer(1, 60)).executes(ctx -> {
							ArmyBattle.s().armyConfigMinutes = IntegerArgumentType.getInteger(ctx, "minuten");
							ChallengeManager.get().save();
							ok(ctx, "Zeit zum Wellen-Bauen: " + ArmyBattle.s().armyConfigMinutes + " Minuten");
							return 1;
						})))
				.then(Commands.literal("eier")
						.then(Commands.argument("prozent", DoubleArgumentType.doubleArg(0, 100)).executes(ctx -> {
							ArmyBattle.s().armyEggChance = DoubleArgumentType.getDouble(ctx, "prozent");
							ChallengeManager.get().save();
							ok(ctx, "Spawn-Ei-Chance beim Abbauen: " + ArmyBattle.s().armyEggChance + " %");
							return 1;
						})))
				.then(Commands.literal("arena")
						.executes(ctx -> {
							String arena = ArmyBattle.s().armyArena;
							ok(ctx, "Arena: " + (arena.isBlank() ? "eingebaute Halle" : arena));
							return 1;
						})
						.then(Commands.literal("standard").executes(ctx -> {
							ArmyBattle.s().armyArena = "";
							ChallengeManager.get().save();
							ok(ctx, "Arena: eingebaute Halle");
							return 1;
						}))
						.then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
							ArmyBattle.s().armyArena = StringArgumentType.getString(ctx, "name").trim();
							ChallengeManager.get().save();
							ok(ctx, "Arena-Map: " + ArmyBattle.s().armyArena + " (Lapislazuliblock = Mob-Spawn, Diamantblock = Spieler-Start)");
							return 1;
						})))
				.then(Commands.literal("kills").executes(ArmyCommands::kills)));

		for (String name : new String[] {"backpack", "bp", "rucksack"}) {
			dispatcher.register(Commands.literal(name).executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				ArmyTeam team = ArmyBattle.get().teamOf(player);
				if (team == null || !ArmyBattle.get().active()) {
					ctx.getSource().sendFailure(Component.literal("Den Team-Rucksack gibt es nur im Mob Army Battle."));
					return 0;
				}
				TeamBackpack.open(player, team);
				return 1;
			}));
		}

		dispatcher.register(Commands.literal("zuschauen").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			String error = ArmyBattle.get().spectate(player);
			if (error != null) {
				ctx.getSource().sendFailure(Component.literal(error));
				return 0;
			}
			return 1;
		}));
		dispatcher.register(Commands.literal("spawn").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			ArmyBattle army = ArmyBattle.get();
			ArmyTeam team = army.teamOf(player);
			if (army.active() && team != null && !army.isFinished(team)) {
				ctx.getSource().sendFailure(Component.literal("Das geht erst, wenn dein Team fertig ist."));
				return 0;
			}
			army.toSpawn(ctx.getSource().getServer(), player);
			return 1;
		}));

		dispatcher.register(Commands.literal("wellen").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			if (ArmyBattle.get().teamOf(player) == null) {
				ctx.getSource().sendFailure(Component.literal("Du bist in keinem Army-Team."));
				return 0;
			}
			ArmyGui.open(player);
			return 1;
		}));
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		ArmyBattle army = ArmyBattle.get();
		ChallengeManagerHolder.send(ctx, Component.literal("Mob Army Battle: " + (army.active() ? "läuft – Phase " + phaseName(army.phase()) : "aus"))
				.withStyle(ChatFormatting.GOLD));
		ChallengeManagerHolder.send(ctx, army.teamList());
		ChallengeManagerHolder.send(ctx, Component.literal("Farmzeit " + ArmyBattle.s().armyFarmMinutes + " min · Wellen-Bauen "
				+ ArmyBattle.s().armyConfigMinutes + " min · Spawn-Eier " + ArmyBattle.s().armyEggChance + " %").withStyle(ChatFormatting.GRAY));
		ChallengeManagerHolder.send(ctx, Component.literal("/army team <spieler> rot|blau · /army start · /army weiter · /army stop")
				.withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private static int start(CommandContext<CommandSourceStack> ctx) {
		String error = ArmyBattle.get().start(ctx.getSource().getServer());
		if (error != null) {
			ctx.getSource().sendFailure(Component.literal(error));
			return 0;
		}
		return 1;
	}

	private static int setTeam(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "spieler");
		String name = StringArgumentType.getString(ctx, "team");
		String uuid = player.getUUID().toString();
		ArmyBattle.s().playerNames.put(uuid, player.getName().getString());
		if (name.equalsIgnoreCase("keins")) {
			ArmyBattle.s().armyTeams.remove(uuid);
			ok(ctx, player.getName().getString() + " ist in keinem Team mehr.");
		} else {
			ArmyTeam team = ArmyTeam.parse(name);
			if (team == null) {
				ctx.getSource().sendFailure(Component.literal("Team gibt es nicht – rot oder blau"));
				return 0;
			}
			ArmyBattle.s().armyTeams.put(uuid, team.name());
			ok(ctx, player.getName().getString() + " ist jetzt in " + team.title() + ".");
		}
		ChallengeManager.get().save();
		return 1;
	}

	private static int kills(CommandContext<CommandSourceStack> ctx) {
		for (ArmyTeam team : ArmyTeam.values()) {
			Map<String, Integer> pool = ArmyBattle.get().pool(team);
			int total = 0;
			for (int count : pool.values()) {
				total += count;
			}
			ChallengeManagerHolder.send(ctx, Component.literal(team.title() + ": " + total + " Mobs getötet").withStyle(team.color));
		}
		return 1;
	}

	private static String phaseName(String phase) {
		return switch (phase) {
			case ArmyBattle.FARM -> "Farmen";
			case ArmyBattle.CONFIG -> "Wellen bauen";
			case ArmyBattle.FIGHT -> "Kampf";
			default -> "vorbei";
		};
	}

	private static void ok(CommandContext<CommandSourceStack> ctx, String text) {
		ChallengeManagerHolder.send(ctx, Component.literal(text).withStyle(ChatFormatting.GREEN));
	}

	private static final class ChallengeManagerHolder {
		static void send(CommandContext<CommandSourceStack> ctx, Component text) {
			ctx.getSource().sendSuccess(() -> text, false);
		}
	}
}
