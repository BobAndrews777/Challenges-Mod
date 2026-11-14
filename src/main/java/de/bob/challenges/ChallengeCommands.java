package de.bob.challenges;

import java.util.ArrayList;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

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
