package de.bob.challenges;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

import de.bob.challenges.split.SplitManager;
import de.bob.challenges.split.SplitType;

/**
 * /challenge  – öffnet das Menü
 * /challenge status
 * /challenge reset [confirm] – neue Welt
 * /timer start | pause | reset
 */
public final class ChallengeCommands {
	private ChallengeCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> registerAll(dispatcher));
	}

	private static void registerAll(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("challenge")
				.requires(PermissionPredicates.require(ChallengesMod.id("admin"), PermissionLevel.GAMEMASTERS))
				.executes(ChallengeCommands::openGui)
				.then(Commands.literal("menu").executes(ChallengeCommands::openGui))
				.then(Commands.literal("status").executes(ChallengeCommands::status))
				.then(Commands.literal("reset")
						.executes(ChallengeCommands::askReset)
						.then(Commands.literal("confirm").executes(ChallengeCommands::confirmReset))));

		dispatcher.register(Commands.literal("timer")
				.requires(PermissionPredicates.require(ChallengesMod.id("admin"), PermissionLevel.GAMEMASTERS))
				.then(Commands.literal("start").executes(ChallengeCommands::timerStart))
				.then(Commands.literal("resume").executes(ChallengeCommands::timerStart))
				.then(Commands.literal("pause").executes(ChallengeCommands::timerPause))
				.then(Commands.literal("reset").executes(ChallengeCommands::timerReset)));

		dispatcher.register(Commands.literal("split")
				.requires(PermissionPredicates.require(ChallengesMod.id("admin"), PermissionLevel.GAMEMASTERS))
				.executes(ChallengeCommands::splitStatus)
				.then(Commands.literal("status").executes(ChallengeCommands::splitStatus))
				.then(Commands.literal("an").executes(ctx -> setSplitMode(ctx, true)))
				.then(Commands.literal("aus").executes(ctx -> setSplitMode(ctx, false)))
				.then(Commands.literal("farben").executes(ChallengeCommands::splitColors))
				.then(Commands.literal("zeit")
						.then(Commands.argument("min", IntegerArgumentType.integer(1, 120))
								.then(Commands.argument("max", IntegerArgumentType.integer(1, 180))
										.executes(ChallengeCommands::splitTime))))
				.then(Commands.literal("jokerchance")
						.then(Commands.argument("prozent", IntegerArgumentType.integer(0, 100))
								.executes(ChallengeCommands::splitJokerChance)))
				.then(Commands.literal("test")
						.then(Commands.argument("split", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(implementedNames(), builder))
								.executes(ChallengeCommands::splitTest))));

		dispatcher.register(Commands.literal("joker")
				.requires(PermissionPredicates.require(ChallengesMod.id("admin"), PermissionLevel.GAMEMASTERS))
				.executes(ChallengeCommands::jokerList)
				.then(Commands.argument("split", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(activeNames(), builder))
						.executes(ChallengeCommands::jokerUse)));
	}

	// ------------------------------------------------------------------
	// Split-Challenge
	// ------------------------------------------------------------------

	private static List<String> implementedNames() {
		List<String> names = new ArrayList<>();
		for (SplitType type : SplitType.values()) {
			if (type.implemented) {
				names.add(type.commandName());
			}
		}
		return names;
	}

	private static List<String> activeNames() {
		List<String> names = new ArrayList<>();
		for (String name : ChallengeManager.get().settings().activeSplits) {
			names.add(name.toLowerCase(Locale.ROOT));
		}
		return names;
	}

	private static int splitStatus(CommandContext<CommandSourceStack> ctx) {
		String text = SplitManager.get().statusText();
		ctx.getSource().sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GOLD), false);
		return 1;
	}

	private static int setSplitMode(CommandContext<CommandSourceStack> ctx, boolean on) {
		ChallengeManager.get().settings().splitMode = on;
		ChallengeManager.get().save();
		MinecraftServer server = ctx.getSource().getServer();
		if (on && ChallengeManager.get().isRunning()) {
			SplitManager.get().onTimerStart(server);
		} else {
			SplitManager.get().sync(server);
		}
		ChallengeManager.broadcast(server, Component.literal("Split-Challenge: " + (on ? "AN" : "AUS")).withStyle(ChatFormatting.WHITE));
		return 1;
	}

	private static int splitColors(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		SplitManager.get().assignColors(server, true);
		ChallengeManager.get().save();
		SplitManager.get().sync(server);
		return 1;
	}

	private static int splitTime(CommandContext<CommandSourceStack> ctx) {
		int min = IntegerArgumentType.getInteger(ctx, "min");
		int max = IntegerArgumentType.getInteger(ctx, "max");
		ChallengeSettings s = ChallengeManager.get().settings();
		s.splitMinMinutes = Math.min(min, max);
		s.splitMaxMinutes = Math.max(min, max);
		s.clampValues();
		ChallengeManager.get().save();
		ctx.getSource().sendSuccess(() -> Component.literal("Neue Splits kommen jetzt alle " + s.splitMinMinutes + " bis " + s.splitMaxMinutes + " Minuten. (Gilt ab dem nächsten Split.)")
				.withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int splitJokerChance(CommandContext<CommandSourceStack> ctx) {
		int percent = IntegerArgumentType.getInteger(ctx, "prozent");
		ChallengeManager.get().settings().jokerChancePercent = percent;
		ChallengeManager.get().save();
		ctx.getSource().sendSuccess(() -> Component.literal("Joker-Chance: " + percent + " % pro Ziehung").withStyle(ChatFormatting.GREEN), false);
		return 1;
	}

	private static int splitTest(CommandContext<CommandSourceStack> ctx) {
		SplitType type = SplitType.byCommandName(StringArgumentType.getString(ctx, "split"));
		if (type == null || !type.implemented) {
			ctx.getSource().sendFailure(Component.literal("Diesen Split gibt es (noch) nicht."));
			return 0;
		}
		MinecraftServer server = ctx.getSource().getServer();
		if (ChallengeManager.get().settings().playerColors.isEmpty()) {
			SplitManager.get().assignColors(server, false);
		}
		SplitManager.get().activate(server, type);
		return 1;
	}

	private static int jokerList(CommandContext<CommandSourceStack> ctx) {
		ChallengeSettings s = ChallengeManager.get().settings();
		ctx.getSource().sendSuccess(() -> Component.literal("Übrige Joker: " + s.jokersLeft).withStyle(ChatFormatting.GOLD), false);
		for (String name : s.activeSplits) {
			SplitType type = SplitType.byCommandName(name);
			if (type == null) {
				continue;
			}
			Component line = Component.literal(" • " + type.displayName + " ").withStyle(ChatFormatting.WHITE)
					.append(Component.literal("[Joker]").withStyle(style -> style
							.withColor(ChatFormatting.GOLD)
							.withClickEvent(new ClickEvent.RunCommand("/joker " + type.commandName()))
							.withHoverEvent(new HoverEvent.ShowText(Component.literal("Diesen Split für immer abschalten")))));
			ctx.getSource().sendSuccess(() -> line, false);
		}
		return 1;
	}

	private static int jokerUse(CommandContext<CommandSourceStack> ctx) {
		SplitType type = SplitType.byCommandName(StringArgumentType.getString(ctx, "split"));
		if (type == null) {
			ctx.getSource().sendFailure(Component.literal("Unbekannter Split."));
			return 0;
		}
		if (ChallengeManager.get().settings().jokersLeft <= 0) {
			ctx.getSource().sendFailure(Component.literal("Ihr habt keine Joker mehr."));
			return 0;
		}
		if (!SplitManager.get().useJoker(ctx.getSource().getServer(), type)) {
			ctx.getSource().sendFailure(Component.literal(type.displayName + " ist gerade nicht aktiv."));
			return 0;
		}
		return 1;
	}

	private static int openGui(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		SettingsGui.open(player);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		ChallengeManager m = ChallengeManager.get();
		ChallengeSettings s = m.settings();
		String state = m.isEnded() ? "beendet" : m.isRunning() ? "läuft" : "pausiert";
		ArrayList<String> active = new ArrayList<>();
		if (s.dragonGoal) active.add("Ziel Enderdrache");
		if (s.deathEndsChallenge) active.add("Ein Tod = Ende");
		if (s.uhc) active.add("UHC");
		if (s.sharedHearts) active.add("Geteilte Herzen");
		if (s.randomBlockDrops) active.add("Zufalls-Blockdrops");
		if (s.randomMobDrops) active.add("Zufalls-Mobdrops");
		if (s.randomItem) active.add("Zufalls-Item (" + s.randomItemSeconds + "s)");
		if (s.jumpDamage) active.add("Sprung-Schaden (" + s.jumpDamageHearts + " Herz)");
		if (s.sneakDeath) active.add("Schleich-Tod");
		if (s.forceBlock) active.add("Force Block");
		if (s.forceMob) active.add("Force Mob");
		if (s.forceItem) active.add("Force Item");

		ctx.getSource().sendSuccess(() -> Component.literal("Timer: " + ChallengeManager.formatTime(s.timerTicks) + " (" + state + ")")
				.withStyle(ChatFormatting.GOLD), false);
		ctx.getSource().sendSuccess(() -> Component.literal("Aktiv: " + (active.isEmpty() ? "nichts" : String.join(", ", active)))
				.withStyle(ChatFormatting.GRAY), false);
		return 1;
	}

	private static int askReset(CommandContext<CommandSourceStack> ctx) {
		Component confirm = Component.literal("[Ja, neue Welt erstellen]")
				.withStyle(style -> style
						.withColor(ChatFormatting.RED)
						.withBold(true)
						.withClickEvent(new ClickEvent.RunCommand("/challenge reset confirm"))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Löscht die Welt und stoppt den Server"))));
		ctx.getSource().sendSuccess(() -> Component.literal("Die aktuelle Welt wird gelöscht und der Server gestoppt. Beim nächsten Start gibt es eine neue Welt. ")
				.withStyle(ChatFormatting.YELLOW).append(confirm), false);
		return 1;
	}

	private static int confirmReset(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		if (!WorldReset.isSupported()) {
			ctx.getSource().sendFailure(Component.literal("Welt-Reset geht nur auf einem richtigen Server, nicht im Einzelspieler."));
			return 0;
		}
		if (!WorldReset.scheduleReset()) {
			ctx.getSource().sendFailure(Component.literal("Konnte den Reset nicht vormerken – siehe Server-Log."));
			return 0;
		}
		ChallengeManager.get().onServerStopping();
		Component kickMessage = Component.literal("Neue Welt wird erstellt!\nDer Server startet gleich neu.").withStyle(ChatFormatting.GOLD);
		for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
			player.connection.disconnect(kickMessage);
		}
		server.halt(false);
		return 1;
	}

	private static int timerStart(CommandContext<CommandSourceStack> ctx) {
		ChallengeManager m = ChallengeManager.get();
		if (m.isRunning()) {
			ctx.getSource().sendFailure(Component.literal("Der Timer läuft schon."));
			return 0;
		}
		if (!m.start(ctx.getSource().getServer())) {
			ctx.getSource().sendFailure(Component.literal("Die Challenge ist beendet. Nutze /timer reset oder /challenge reset."));
			return 0;
		}
		return 1;
	}

	private static int timerPause(CommandContext<CommandSourceStack> ctx) {
		ChallengeManager m = ChallengeManager.get();
		if (!m.isRunning()) {
			ctx.getSource().sendFailure(Component.literal("Der Timer ist schon pausiert."));
			return 0;
		}
		m.pause(ctx.getSource().getServer());
		return 1;
	}

	private static int timerReset(CommandContext<CommandSourceStack> ctx) {
		ChallengeManager.get().resetTimer(ctx.getSource().getServer());
		return 1;
	}
}
