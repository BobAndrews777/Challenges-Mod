package de.bob.challenges.army;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import de.bob.challenges.BossBars;
import de.bob.challenges.ChallengeManager;
import de.bob.challenges.ChallengeSettings;
import de.bob.challenges.mobs.MobChallenge;

/**
 * Mob Army Battle:
 * 1. Farmen (Standard 2 Std.): Jedes Team in seiner eigenen Parallelwelt, jeder getötete Mob kommt in den Team-Pool.
 * 2. Zusammenstellen: Jedes Team baut aus seinem Pool 3 Wellen – für das andere Team.
 * 3. Kampf: Beide Teams gleichzeitig in ihrer eigenen Arena gegen die Wellen des Gegners. Wer zuerst alle 3 schafft, gewinnt.
 */
public final class ArmyBattle {
	public static final String FARM = "FARM";
	public static final String CONFIG = "CONFIG";
	public static final String FIGHT = "FIGHT";
	public static final String END = "END";

	private static final int RESPAWN_DELAY = 2 * 60 * 20;
	private static final int SPAWN_INTERVAL = 20;

	private static final ArmyBattle INSTANCE = new ArmyBattle();
	private final Random random = new Random();
	private final Map<ArmyTeam, Fight> fights = new EnumMap<>(ArmyTeam.class);
	private final Set<UUID> armyMobs = new HashSet<>();
	private final Set<ArmyTeam> finished = java.util.EnumSet.noneOf(ArmyTeam.class);
	private ArmyTeam winner;
	/** Spieler → Tick, ab dem er wieder in die Arena darf */
	private final Map<UUID, Long> respawnAt = new HashMap<>();
	private List<Item> eggs;
	private long tick;

	public static ArmyBattle get() {
		return INSTANCE;
	}

	static ChallengeSettings s() {
		return ChallengeManager.get().settings();
	}

	public boolean active() {
		return s().armyActive;
	}

	public String phase() {
		return s().armyPhase;
	}

	public ArmyTeam teamOf(Player player) {
		return player == null ? null : ArmyTeam.parse(s().armyTeams.get(player.getUUID().toString()));
	}

	public boolean isArmyMob(Entity entity) {
		return !armyMobs.isEmpty() && armyMobs.contains(entity.getUUID());
	}

	// ------------------------------------------------------------------
	// Pool und Wellen
	// ------------------------------------------------------------------

	Map<String, Integer> pool(ArmyTeam team) {
		return s().armyPools.computeIfAbsent(team.name(), k -> new HashMap<>());
	}

	List<List<String>> waves(ArmyTeam team) {
		List<List<String>> waves = s().armyWaves.computeIfAbsent(team.name(), k -> new ArrayList<>());
		while (waves.size() < 3) {
			waves.add(new ArrayList<>());
		}
		return waves;
	}

	int used(ArmyTeam team, String mob) {
		int used = 0;
		for (List<String> wave : waves(team)) {
			for (String entry : wave) {
				if (entry.equals(mob)) {
					used++;
				}
			}
		}
		return used;
	}

	int available(ArmyTeam team, String mob) {
		return pool(team).getOrDefault(mob, 0) - used(team, mob);
	}

	boolean addToWave(ArmyTeam team, int wave, String mob) {
		if (!CONFIG.equals(phase()) || s().armyReady.contains(team.name()) || available(team, mob) <= 0) {
			return false;
		}
		waves(team).get(wave).add(mob);
		ChallengeManager.get().save();
		return true;
	}

	boolean removeFromWave(ArmyTeam team, int wave, String mob) {
		if (!CONFIG.equals(phase()) || s().armyReady.contains(team.name())) {
			return false;
		}
		List<String> list = waves(team).get(wave);
		int index = list.lastIndexOf(mob);
		if (index < 0) {
			return false;
		}
		list.remove(index);
		ChallengeManager.get().save();
		return true;
	}

	boolean insertAt(ArmyTeam team, int wave, int index, String mob) {
		if (!CONFIG.equals(phase()) || s().armyReady.contains(team.name()) || available(team, mob) <= 0) {
			return false;
		}
		List<String> list = waves(team).get(wave);
		list.add(Math.max(0, Math.min(index, list.size())), mob);
		ChallengeManager.get().save();
		return true;
	}

	boolean removeAt(ArmyTeam team, int wave, int index) {
		if (!CONFIG.equals(phase()) || s().armyReady.contains(team.name())) {
			return false;
		}
		List<String> list = waves(team).get(wave);
		if (index < 0 || index >= list.size()) {
			return false;
		}
		list.remove(index);
		ChallengeManager.get().save();
		return true;
	}

	void toggleReady(MinecraftServer server, ArmyTeam team) {
		if (!CONFIG.equals(phase())) {
			return;
		}
		if (s().armyReady.contains(team.name())) {
			s().armyReady.remove(team.name());
			broadcast(server, Component.literal(team.title() + " ist doch noch nicht fertig.").withStyle(team.color));
		} else {
			s().armyReady.add(team.name());
			broadcast(server, Component.literal(team.title() + " ist mit seinen Wellen fertig!").withStyle(team.color));
		}
		ChallengeManager.get().save();
		if (s().armyReady.contains(ArmyTeam.ROT.name()) && s().armyReady.contains(ArmyTeam.BLAU.name())) {
			startFight(server);
		}
	}

	static EntityType<?> type(String mob) {
		Identifier id = Identifier.tryParse(mob);
		return id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? BuiltInRegistries.ENTITY_TYPE.getValue(id) : null;
	}

	static Component mobName(String mob) {
		EntityType<?> type = type(mob);
		return type == null ? Component.literal(mob) : type.getDescription();
	}

	static Item egg(String mob) {
		Identifier id = Identifier.tryParse(mob);
		if (id != null) {
			Identifier eggId = Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spawn_egg");
			if (BuiltInRegistries.ITEM.containsKey(eggId)) {
				return BuiltInRegistries.ITEM.getValue(eggId);
			}
		}
		return Items.EGG;
	}

	// ------------------------------------------------------------------
	// Ablauf
	// ------------------------------------------------------------------

	/** Startet eine neue Runde: Teams auffüllen, Pools leeren, alle in ihre Team-Welt. */
	public String start(MinecraftServer server) {
		for (ArmyTeam team : ArmyTeam.values()) {
			if (server.getLevel(team.world) == null) {
				return "Die Team-Welten fehlen – ist die Mod auf dem Server richtig installiert?";
			}
		}
		List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
		// Spieler ohne Team zufällig verteilen (das kleinere Team zuerst)
		Collections.shuffle(players, random);
		for (ServerPlayer player : players) {
			s().playerNames.put(player.getUUID().toString(), player.getName().getString());
			if (teamOf(player) == null) {
				ArmyTeam smaller = count(ArmyTeam.ROT) <= count(ArmyTeam.BLAU) ? ArmyTeam.ROT : ArmyTeam.BLAU;
				s().armyTeams.put(player.getUUID().toString(), smaller.name());
			}
		}
		if (count(ArmyTeam.ROT) == 0 || count(ArmyTeam.BLAU) == 0) {
			return "Für Mob Army Battle braucht jedes Team mindestens einen Spieler.";
		}
		stopFights(server);
		s().armyActive = true;
		s().armyPhase = FARM;
		s().armyTicksLeft = s().armyFarmMinutes * 60 * 20;
		s().armyPools.clear();
		s().armyWaves.clear();
		s().armyReady.clear();
		respawnAt.clear();
		TeamBackpack.clear();
		// neue Runde = Randomizer neu mischen
		ChallengeManager.get().settings().dropSeed = random.nextLong();
		ChallengeManager.get().rebuildDropMap();
		ChallengeManager.get().save();

		BlockPos spawn = server.overworld().getRespawnData().pos();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ArmyTeam team = teamOf(player);
			if (team == null) {
				continue;
			}
			player.getInventory().clearContent();
			player.setHealth(player.getMaxHealth());
			player.getFoodData().setFoodLevel(20);
			player.setGameMode(GameType.SURVIVAL);
			ServerLevel world = server.getLevel(team.world);
			int y = safeY(world, spawn.getX(), spawn.getZ());
			player.teleportTo(world, spawn.getX() + 0.5, y, spawn.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
			title(player, Component.literal(team.title()).withStyle(team.color, ChatFormatting.BOLD),
					Component.literal("Tötet so viele Mobs wie möglich!").withStyle(ChatFormatting.YELLOW));
		}
		if (!ChallengeManager.get().isRunning()) {
			ChallengeManager.get().start(server);
		}
		broadcast(server, Component.literal("Mob Army Battle startet! " + s().armyFarmMinutes
				+ " Minuten Zeit zum Farmen – jedes Team in seiner eigenen Welt.").withStyle(ChatFormatting.GOLD));
		broadcast(server, teamList());
		return null;
	}

	public void stop(MinecraftServer server) {
		stopFights(server);
		s().armyActive = false;
		s().armyPhase = FARM;
		respawnAt.clear();
		ChallengeManager.get().save();
		hideBars(server);
		ArmyGui.closeAll();
	}

	/** Springt sofort in die nächste Phase (zum Testen oder wenn alle fertig sind). */
	public void skip(MinecraftServer server) {
		switch (phase()) {
			case FARM -> startConfig(server);
			case CONFIG -> startFight(server);
			default -> {
			}
		}
	}

	private void startConfig(MinecraftServer server) {
		s().armyPhase = CONFIG;
		s().armyTicksLeft = s().armyConfigMinutes * 60 * 20;
		s().armyReady.clear();
		ChallengeManager.get().save();
		broadcast(server, Component.literal("Die Zeit ist um! Stellt jetzt eure 3 Wellen für das andere Team zusammen ("
				+ s().armyConfigMinutes + " Minuten). Menü: /wellen").withStyle(ChatFormatting.GOLD));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (teamOf(player) != null) {
				title(player, Component.literal("Wellen bauen!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
						Component.literal("Rechtsklick +1, Linksklick −1").withStyle(ChatFormatting.YELLOW));
				ArmyGui.open(player);
			}
		}
	}

	private void startFight(MinecraftServer server) {
		ArmyGui.closeAll();
		s().armyPhase = FIGHT;
		s().armyTicksLeft = 0;
		ChallengeManager.get().save();
		stopFights(server);
		finished.clear();
		winner = null;
		setMobGriefing(server, false);
		for (ArmyTeam team : ArmyTeam.values()) {
			ServerLevel level = server.getLevel(team.world);
			if (level == null) {
				continue;
			}
			List<String> problems = new ArrayList<>();
			ArenaBuilder.Arena arena = ArenaBuilder.build(level, s().armyArena, problems);
			if (team == ArmyTeam.ROT) {
				for (String problem : problems) {
					broadcast(server, Component.literal(problem).withStyle(ChatFormatting.RED));
				}
			}
			// Übrig gebliebene Mobs und Items einer früheren Runde entfernen
			// (ganzer Arena-Bereich hoch über 0/0, auch wo früher eine andere Arena stand)
			net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
					Math.min(arena.min().getX(), -20) - 8, Math.min(arena.min().getY(), 278) - 8, Math.min(arena.min().getZ(), -20) - 8,
					Math.max(arena.max().getX(), 20) + 8, level.getMaxY(), Math.max(arena.max().getZ(), 20) + 8);
			for (Entity old : level.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player))) {
				old.discard();
			}
			Fight fight = new Fight(team, level, arena);
			fights.put(team, fight);
			fight.loadWave(0, waves(team.other()).get(0));
			fight.pause = 10 * 20;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ArmyTeam team = teamOf(player);
			if (team != null) {
				toArena(server, player, team);
				title(player, Component.literal("Kampf!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
						Component.literal("Besiegt die 3 Wellen von " + team.other().title()).withStyle(ChatFormatting.YELLOW));
			}
		}
		broadcast(server, Component.literal("Der Kampf beginnt in 10 Sekunden! Wer zuerst alle 3 Wellen besiegt, gewinnt.")
				.withStyle(ChatFormatting.GOLD));
	}

	/** Ein Team hat alle 3 Wellen geschafft. Das erste gewinnt, das andere darf noch zu Ende kämpfen. */
	private void teamDone(MinecraftServer server, ArmyTeam team) {
		finished.add(team);
		boolean first = winner == null;
		if (first) {
			winner = team;
			ChallengeManager.get().win(server, team.title() + " hat alle 3 Wellen zuerst besiegt");
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				ArmyTeam own = teamOf(player);
				title(player, Component.literal(team.title() + " gewinnt!").withStyle(team.color, ChatFormatting.BOLD),
						Component.literal(own == team ? "Glückwunsch!" : own == null ? "" : "Ihr könnt trotzdem noch zu Ende kämpfen").withStyle(ChatFormatting.YELLOW));
			}
		} else {
			broadcast(server, Component.literal(team.title() + " hat jetzt auch alle 3 Wellen geschafft.").withStyle(team.color));
		}
		// Fertige Spieler: zurück zum Spawn
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (teamOf(player) == team) {
				respawnAt.remove(player.getUUID());
				toSpawn(server, player);
				if (finished.size() < ArmyTeam.values().length) {
					player.sendSystemMessage(Component.literal("Dem anderen Team zuschauen: /zuschauen  ·  zurück zum Spawn: /spawn")
							.withStyle(ChatFormatting.GRAY));
				}
			}
		}
		Fight fight = fights.get(team);
		if (fight != null) {
			fight.clearMobs();
		}
		TeamBars.hide(server, "army_" + team.name().toLowerCase());
		if (finished.size() >= ArmyTeam.values().length) {
			end(server);
		}
	}

	/** Alles vorbei: Regeln zurücksetzen, Zuschauer zurück zum Spawn. */
	private void end(MinecraftServer server) {
		s().armyPhase = END;
		hideBars(server);
		stopFights(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() && teamOf(player) != null) {
				toSpawn(server, player);
			}
		}
		s().armyActive = false;
		ChallengeManager.get().save();
	}

	/** Zum Spawn der normalen Oberwelt, im Überlebensmodus */
	public void toSpawn(MinecraftServer server, ServerPlayer player) {
		ServerLevel overworld = server.overworld();
		BlockPos spawn = overworld.getRespawnData().pos();
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(overworld, spawn.getX() + 0.5, safeY(overworld, spawn.getX(), spawn.getZ()), spawn.getZ() + 0.5,
				Set.of(), player.getYRot(), player.getXRot(), true);
	}

	/** Als Zuschauer in die Arena des anderen Teams (nur wenn das eigene Team fertig ist oder man in keinem Team ist). */
	public String spectate(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (!active() || !FIGHT.equals(phase()) || server == null) {
			return "Gerade läuft kein Kampf.";
		}
		ArmyTeam own = teamOf(player);
		if (own != null && !finished.contains(own)) {
			return "Erst die eigenen Wellen besiegen!";
		}
		for (ArmyTeam team : ArmyTeam.values()) {
			Fight fight = fights.get(team);
			if (team != own && fight != null && !finished.contains(team)) {
				BlockPos view = fight.arena.center();
				player.setGameMode(GameType.SPECTATOR);
				player.teleportTo(fight.level, view.getX() + 0.5, view.getY(), view.getZ() + 0.5, Set.of(), player.getYRot(), 30, true);
				return null;
			}
		}
		return "Es kämpft niemand mehr.";
	}

	public boolean isFinished(ArmyTeam team) {
		return finished.contains(team);
	}

	private void stopFights(MinecraftServer server) {
		if (!fights.isEmpty()) {
			setMobGriefing(server, true);
		}
		for (Fight fight : fights.values()) {
			for (UUID id : fight.alive) {
				Entity entity = fight.level.getEntity(id);
				if (entity != null) {
					entity.discard();
				}
			}
		}
		fights.clear();
		armyMobs.clear();
	}

	// ------------------------------------------------------------------
	// Tick
	// ------------------------------------------------------------------

	public void tick(MinecraftServer server) {
		if (!active()) {
			return;
		}
		tick++;
		if (tick % 5 == 0) {
			keepInTeamWorlds(server);
		}
		switch (phase()) {
			case FARM -> {
				s().armyTicksLeft--;
				if (tick % 20 == 0) {
					BossBars.show(server, "army_phase", Component.literal("Mob Army – Farmen: " + time(s().armyTicksLeft))
							.withStyle(ChatFormatting.GOLD), s().armyTicksLeft / 20, s().armyFarmMinutes * 60, BossEventColors.YELLOW);
				}
				if (tick % 1200 == 0) {
					TeamBackpack.save(server);
					ChallengeManager.get().save();
				}
				if (s().armyTicksLeft <= 0) {
					startConfig(server);
				}
			}
			case CONFIG -> {
				s().armyTicksLeft--;
				if (tick % 20 == 0) {
					BossBars.show(server, "army_phase", Component.literal("Wellen zusammenstellen: " + time(s().armyTicksLeft)
							+ "  (/wellen)").withStyle(ChatFormatting.GOLD), s().armyTicksLeft / 20, s().armyConfigMinutes * 60, BossEventColors.YELLOW);
					ArmyGui.refreshAll();
				}
				if (s().armyTicksLeft <= 0) {
					startFight(server);
				}
			}
			case FIGHT -> {
				if (fights.isEmpty()) {
					// z. B. nach einem Server-Neustart: Kampf neu aufsetzen
					startFight(server);
					return;
				}
				BossBars.hide(server, "army_phase");
				tickRespawns(server);
				for (Fight fight : new ArrayList<>(fights.values())) {
					if (!finished.contains(fight.team) && fight.tick(server)) {
						teamDone(server, fight.team);
						if (!active()) {
							return;
						}
					}
				}
			}
			default -> {
			}
		}
	}

	/** Team-Spieler, die in der normalen Oberwelt/im normalen Nether landen (Respawn, End-Portal …), kommen in ihre Team-Welt – an dieselbe Stelle. */
	private void keepInTeamWorlds(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ArmyTeam team = teamOf(player);
			if (team == null || !player.isAlive() || END.equals(phase()) || finished.contains(team)) {
				continue;
			}
			ResourceKey<Level> key = player.level().dimension();
			if (key.equals(Level.END)) {
				// Das End ist während Mob Army Battle gesperrt
				ServerLevel world = server.getLevel(team.world);
				if (world != null) {
					BlockPos spawn = server.overworld().getRespawnData().pos();
					int y = safeY(world, spawn.getX(), spawn.getZ());
					player.teleportTo(world, spawn.getX() + 0.5, y, spawn.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
					player.connection.send(new ClientboundSetActionBarTextPacket(
							Component.literal("Das End ist während Mob Army Battle gesperrt").withStyle(ChatFormatting.RED)));
				}
				continue;
			}
			ResourceKey<Level> target = null;
			if (key.equals(Level.OVERWORLD) || key.equals(team.other().world)) {
				target = team.world;
			} else if (key.equals(Level.NETHER) || key.equals(team.other().nether)) {
				target = team.nether;
			}
			if (target == null) {
				continue;
			}
			ServerLevel level = server.getLevel(target);
			if (level == null) {
				continue;
			}
			if (FIGHT.equals(phase())) {
				toArena(server, player, team);
			} else {
				player.teleportTo(level, player.getX(), player.getY(), player.getZ(), Set.of(), player.getYRot(), player.getXRot(), true);
			}
		}
	}

	// ------------------------------------------------------------------
	// Tod und Respawn im Kampf
	// ------------------------------------------------------------------

	public void onRespawn(ServerPlayer player) {
		if (!active() || !FIGHT.equals(phase())) {
			return;
		}
		ArmyTeam team = teamOf(player);
		MinecraftServer server = player.level().getServer();
		if (team == null || server == null) {
			return;
		}
		if (finished.contains(team)) {
			toSpawn(server, player);
			return;
		}
		respawnAt.put(player.getUUID(), tick + RESPAWN_DELAY);
		player.setGameMode(GameType.SPECTATOR);
		ServerLevel level = server.getLevel(team.world);
		if (level != null) {
			Fight fight = fights.get(team);
			BlockPos view = fight != null ? fight.arena.center() : new BlockPos(0, 290, 0);
			player.teleportTo(level, view.getX() + 0.5, view.getY(), view.getZ() + 0.5, Set.of(), player.getYRot(), 60, true);
		}
		player.connection.send(new ClientboundSetActionBarTextPacket(
				Component.literal("In 2 Minuten geht's zurück in die Arena").withStyle(ChatFormatting.GRAY)));
	}

	private void tickRespawns(MinecraftServer server) {
		if (respawnAt.isEmpty()) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Long at = respawnAt.get(player.getUUID());
			if (at == null) {
				continue;
			}
			long left = at - tick;
			if (left <= 0) {
				respawnAt.remove(player.getUUID());
				ArmyTeam team = teamOf(player);
				player.setGameMode(GameType.SURVIVAL);
				if (team != null) {
					toArena(server, player, team);
				}
				player.connection.send(new ClientboundSetActionBarTextPacket(
						Component.literal("Zurück in die Arena!").withStyle(ChatFormatting.GREEN)));
			} else if (left % 20 == 0) {
				player.connection.send(new ClientboundSetActionBarTextPacket(
						Component.literal("Zurück in die Arena in " + time((int) left)).withStyle(ChatFormatting.GRAY)));
			}
		}
	}

	private void toArena(MinecraftServer server, ServerPlayer player, ArmyTeam team) {
		ServerLevel level = server.getLevel(team.world);
		if (level == null || respawnAt.containsKey(player.getUUID()) || finished.contains(team)) {
			return;
		}
		Fight fight = fights.get(team);
		if (fight == null) {
			return;
		}
		List<BlockPos> spawns = fight.arena.playerSpawns();
		BlockPos pos = spawns.get(random.nextInt(spawns.size()));
		player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), player.getYRot(), 0, true);
	}

	// ------------------------------------------------------------------
	// Schutz: Arena unzerstörbar, beim Wellen-Bauen kein Schaden
	// ------------------------------------------------------------------

	/** Im Kampf darf in der Arena nichts abgebaut werden (Platzieren geht). Kreativ-Spieler dürfen alles. */
	public boolean allowBreak(Level level, Player player, BlockPos pos) {
		if (!active() || !FIGHT.equals(phase()) || player.isCreative()) {
			return true;
		}
		ArmyTeam team = ArmyTeam.ofLevel(level.dimension());
		Fight fight = team == null ? null : fights.get(team);
		if (fight == null || !fight.arena.contains(pos.getX(), pos.getY(), pos.getZ())) {
			return true;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(
					Component.literal("In der Arena kann nichts abgebaut werden").withStyle(ChatFormatting.RED)));
		}
		return false;
	}

	/** Im Kampf sind die Team-Welten vor Explosionen geschützt. */
	public boolean protectsBlocks(Level level) {
		return active() && FIGHT.equals(phase()) && ArmyTeam.ofLevel(level.dimension()) != null;
	}

	/** Während die Wellen gebaut werden, bekommen Team-Spieler keinen Schaden. */
	public boolean allowDamage(LivingEntity entity) {
		return !(entity instanceof ServerPlayer player) || !active() || !CONFIG.equals(phase()) || teamOf(player) == null;
	}

	/** Mobs (Creeper, Ghast-Feuerbälle, Endermen …) zerstören im Kampf keine Blöcke in den Team-Welten. */
	private void setMobGriefing(MinecraftServer server, boolean allowed) {
		for (ArmyTeam team : ArmyTeam.values()) {
			ServerLevel level = server.getLevel(team.world);
			if (level != null) {
				var source = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
				server.getCommands().performPrefixedCommand(source, "gamerule minecraft:mob_griefing " + allowed);
				// Im Kampf behält man sein Inventar (sonst liegen die Items in der Arena und verschwinden)
				server.getCommands().performPrefixedCommand(source, "gamerule minecraft:keep_inventory " + !allowed);
			}
		}
	}

	// ------------------------------------------------------------------
	// Kills und Spawn-Eier
	// ------------------------------------------------------------------

	public void onEntityDeath(LivingEntity entity, DamageSource source) {
		if (!active() || !FARM.equals(phase()) || !(entity instanceof Mob) || isArmyMob(entity)
				|| MobChallenge.get().disguises().isDisguise(entity)) {
			return;
		}
		String mob = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
		if (mob.equals("minecraft:ender_dragon")) {
			return;   // der Drache würde alles kaputtmachen
		}
		Entity killer = source.getEntity();
		if (!(killer instanceof ServerPlayer)) {
			killer = entity.getKillCredit();
		}
		if (!(killer instanceof ServerPlayer player)) {
			return;
		}
		ArmyTeam team = teamOf(player);
		if (team == null) {
			return;
		}
		int count = pool(team).merge(mob, 1, Integer::sum);
		player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("+1 ").withStyle(ChatFormatting.GREEN)
				.append(mobName(mob).copy().withStyle(ChatFormatting.WHITE))
				.append(Component.literal("  (Team: " + count + ")").withStyle(ChatFormatting.GRAY))));
	}

	public void onBlockBroken(Level level, Player player, BlockPos pos, BlockState state) {
		if (!active() || !FARM.equals(phase()) || level.isClientSide() || teamOf(player) == null) {
			return;
		}
		if (random.nextDouble() * 100 >= s().armyEggChance) {
			return;
		}
		List<Item> all = eggs();
		if (!all.isEmpty()) {
			Block.popResource(level, pos, new ItemStack(all.get(random.nextInt(all.size()))));
		}
	}

	private List<Item> eggs() {
		if (eggs == null) {
			eggs = new ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM) {
				String path = BuiltInRegistries.ITEM.getKey(item).getPath();
				if (path.endsWith("_spawn_egg") && !path.equals("ender_dragon_spawn_egg")) {
					eggs.add(item);
				}
			}
		}
		return eggs;
	}

	// ------------------------------------------------------------------
	// Ein Kampf (pro Team)
	// ------------------------------------------------------------------

	private final class Fight {
		final ArmyTeam team;
		final ServerLevel level;
		int wave = -1;
		int total;
		final Deque<String> queue = new ArrayDeque<>();
		final List<UUID> alive = new ArrayList<>();
		int cooldown;
		int pause;
		int spawnIndex;
		boolean done;

		final ArenaBuilder.Arena arena;

		Fight(ArmyTeam team, ServerLevel level, ArenaBuilder.Arena arena) {
			this.team = team;
			this.level = level;
			this.arena = arena;
		}

		void clearMobs() {
			for (UUID id : alive) {
				Entity entity = level.getEntity(id);
				if (entity != null) {
					entity.discard();
				}
				armyMobs.remove(id);
			}
			alive.clear();
			queue.clear();
		}

		void loadWave(int index, List<String> mobs) {
			wave = index;
			queue.clear();
			queue.addAll(mobs);
			total = mobs.size();
			cooldown = 0;
		}

		/** @return true, wenn dieses Team gerade alle Wellen geschafft hat */
		boolean tick(MinecraftServer server) {
			if (done) {
				return false;
			}
			if (pause > 0) {
				pause--;
				if (pause % 20 == 0 && pause > 0 && pause <= 100) {
					for (ServerPlayer player : players(server)) {
						title(player, Component.literal(String.valueOf(pause / 20)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
								Component.literal("Welle " + (wave + 1) + " von 3").withStyle(ChatFormatting.YELLOW));
					}
				}
				updateBar(server);
				return false;
			}
			if (!queue.isEmpty() && --cooldown <= 0) {
				spawn(queue.poll());
				cooldown = SPAWN_INTERVAL;
			}
			if (tick % 10 == 0) {
				pruneAndGuard(server);
				updateBar(server);
			}
			if (queue.isEmpty() && alive.isEmpty()) {
				if (wave >= 2) {
					done = true;
					return true;
				}
				// Nur das eigene Team erfährt den Fortschritt
				for (ServerPlayer player : players(server)) {
					player.sendSystemMessage(Component.literal("Welle " + (wave + 1) + " besiegt!").withStyle(team.color));
				}
				loadWave(wave + 1, waves(team.other()).get(wave + 1));
				pause = 6 * 20;
			}
			return false;
		}

		void spawn(String mob) {
			EntityType<?> type = type(mob);
			if (type == null) {
				return;
			}
			Entity entity = type.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (entity == null) {
				return;
			}
			List<BlockPos> points = arena.mobSpawns();
			BlockPos pos = points.get(spawnIndex++ % points.size());
			entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			if (entity instanceof Mob mobEntity) {
				mobEntity.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
				mobEntity.setPersistenceRequired();
				// Arenen ohne festes Dach: Zombies & Co. sollen nicht in der Sonne verbrennen
				mobEntity.addEffect(new net.minecraft.world.effect.MobEffectInstance(
						net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, -1, 0, true, false, false));
			}
			entity.setGlowingTag(true);
			armyMobs.add(entity.getUUID());
			alive.add(entity.getUUID());
			level.addFreshEntity(entity);
			ServerPlayer target = nearestPlayer(entity);
			if (entity instanceof Mob mobEntity && target != null) {
				mobEntity.setTarget(target);
			}
		}

		void pruneAndGuard(MinecraftServer server) {
			alive.removeIf(id -> {
				Entity entity = level.getEntity(id);
				if (entity == null || !entity.isAlive() || entity.isRemoved()) {
					armyMobs.remove(id);
					return true;
				}
				// Ausgebüxt (Enderman-Teleport, rausgeschubst …)? Zurück in die Arena
				if (!arena.contains(entity.getX(), entity.getY(), entity.getZ())) {
					BlockPos point = arena.mobSpawns().get(random.nextInt(arena.mobSpawns().size()));
					entity.teleportTo(point.getX() + 0.5, point.getY(), point.getZ() + 0.5);
				}
				if (entity instanceof Mob mob && tick % 40 == 0 && (mob.getTarget() == null || !mob.getTarget().isAlive())) {
					ServerPlayer target = nearestPlayer(entity);
					if (target != null) {
						mob.setTarget(target);
					}
				}
				return false;
			});
		}

		ServerPlayer nearestPlayer(Entity entity) {
			ServerPlayer best = null;
			double bestDistance = Double.MAX_VALUE;
			for (ServerPlayer player : level.players()) {
				if (teamOf(player) != team || player.isSpectator() || !player.isAlive()) {
					continue;
				}
				double distance = player.distanceToSqr(entity);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = player;
				}
			}
			return best;
		}

		List<ServerPlayer> players(MinecraftServer server) {
			List<ServerPlayer> list = new ArrayList<>();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (teamOf(player) == team) {
					list.add(player);
				}
			}
			return list;
		}

		void updateBar(MinecraftServer server) {
			int left = queue.size() + alive.size();
			String text = team.title() + ": Welle " + (wave + 1) + "/3 – " + (pause > 0 ? "startet gleich" : left + " Mobs übrig");
			TeamBars.show(server, "army_" + team.name().toLowerCase(), Component.literal(text).withStyle(team.color),
					Math.max(0, left), Math.max(1, total), team.barColor, players(server));
		}
	}

	// ------------------------------------------------------------------
	// Hilfen
	// ------------------------------------------------------------------

	int count(ArmyTeam team) {
		int count = 0;
		for (String value : s().armyTeams.values()) {
			if (team.name().equals(value)) {
				count++;
			}
		}
		return count;
	}

	public Component teamList() {
		Component text = Component.literal("Teams: ").withStyle(ChatFormatting.GRAY);
		for (ArmyTeam team : ArmyTeam.values()) {
			List<String> names = new ArrayList<>();
			for (Map.Entry<String, String> entry : s().armyTeams.entrySet()) {
				if (team.name().equals(entry.getValue())) {
					names.add(s().playerNames.getOrDefault(entry.getKey(), "?"));
				}
			}
			text = text.copy().append(Component.literal(team.title() + ": " + (names.isEmpty() ? "–" : String.join(", ", names)) + "  ")
					.withStyle(team.color));
		}
		return text;
	}

	private void hideBars(MinecraftServer server) {
		BossBars.hide(server, "army_phase");
		for (ArmyTeam team : ArmyTeam.values()) {
			TeamBars.hide(server, "army_" + team.name().toLowerCase());
		}
	}

	/**
	 * Sichere Höhe zum Hinstellen. Der Chunk wird dafür erst komplett geladen/generiert –
	 * sonst ist die Höhenkarte noch leer und man landet unter dem Bedrock.
	 */
	static int safeY(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (y > level.getMinY() + 1) {
			return y;
		}
		// Zur Not von oben nach unten den ersten festen Block suchen
		for (int scan = Math.min(level.getMaxY() - 2, 200); scan > level.getMinY() + 1; scan--) {
			BlockPos pos = new BlockPos(x, scan, z);
			if (!level.getBlockState(pos.below()).isAir() && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) {
				return scan;
			}
		}
		return 100;
	}

	static String time(int ticks) {
		int seconds = Math.max(0, ticks / 20);
		int h = seconds / 3600;
		int m = seconds / 60 % 60;
		int sec = seconds % 60;
		return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
	}

	static void broadcast(MinecraftServer server, Component message) {
		ChallengeManager.broadcast(server, message);
	}

	static void title(ServerPlayer player, Component title, Component subtitle) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 10));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	/** kleine Abkürzung, damit oben nicht überall BossEvent steht */
	private static final class BossEventColors {
		static final net.minecraft.world.BossEvent.BossBarColor YELLOW = net.minecraft.world.BossEvent.BossBarColor.YELLOW;
	}
}
