package de.bob.challenges.extra;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import de.bob.challenges.ChallengeManager;
import de.bob.challenges.ChallengeSettings;
import de.bob.challenges.army.ArmyBattle;
import de.bob.challenges.mobs.MobChallenge;

/**
 * Kleinere Challenges:
 * Block-Verschwinden, Laufspur-Löcher, Chunk-Mob, Craft-Mob, Snake, Kill-Effekt, Hit-Tausch, Tick-Uhr.
 */
public final class ExtraChallenges {
	private static final ExtraChallenges INSTANCE = new ExtraChallenges();
	private static final int WALK_DELAY = 60;
	private static final String[] SNAKE_COLORS = {"red", "blue", "lime", "yellow", "purple", "orange", "cyan", "magenta"};
	private static final int[] TICK_RATES = {20, 60, 5};
	private static final String[] TICK_NAMES = {"Normal", "Ganz schnell", "Ganz langsam"};

	private final Random random = new Random();
	private long tick;

	// Block-Verschwinden
	private final Map<UUID, Block> lastBlock = new HashMap<>();
	private final Set<Block> vanished = new HashSet<>();
	private boolean vanishedLoaded;
	private final Map<ResourceKey<Level>, Set<Long>> loadedChunks = new HashMap<>();
	private final Deque<Object[]> chunkQueue = new ArrayDeque<>();

	// Laufspur
	private final Map<String, long[]> walkColumns = new LinkedHashMap<>();

	// Chunk-Mob
	private final Map<UUID, ChunkLock> locks = new HashMap<>();
	private final Map<UUID, Long> lastChunk = new HashMap<>();
	private List<EntityType<?>> mobTypes;

	// Craft-Mob
	private final Map<UUID, Long> lastCraft = new HashMap<>();

	// Snake
	private final Map<String, Trail> trails = new HashMap<>();
	private final Map<UUID, BlockPos> lastSnakePos = new HashMap<>();

	// Kill-Effekt
	private final Map<UUID, List<int[]>> gainedEffects = new HashMap<>();
	private List<Holder<MobEffect>> effects;

	// Hit-Tausch
	private final Map<UUID, Long> lastSwap = new HashMap<>();

	// Tick-Uhr
	private int tickRateIndex;
	private boolean tickRateChanged;

	private record ChunkLock(ResourceKey<Level> level, int chunkX, int chunkZ, UUID mob) {
	}

	private record Trail(ResourceKey<Level> level, BlockPos pos, BlockState original, long until, UUID owner) {
	}

	public static ExtraChallenges get() {
		return INSTANCE;
	}

	private static ChallengeSettings s() {
		return ChallengeManager.get().settings();
	}

	private static boolean running() {
		return ChallengeManager.get().isRunning() && !ChallengeManager.get().isEnded();
	}

	/** Neue Runde (Timer-Reset): alles zurücksetzen */
	public void reset(MinecraftServer server) {
		restoreTrails(server);
		vanished.clear();
		s().vanishedBlocks.clear();
		chunkQueue.clear();
		walkColumns.clear();
		locks.clear();
		lastChunk.clear();
		lastBlock.clear();
		gainedEffects.clear();
		lastSnakePos.clear();
		setTickRate(server, 0);
	}

	// ------------------------------------------------------------------
	// Tick
	// ------------------------------------------------------------------

	public void tick(MinecraftServer server) {
		tick++;
		if (!vanishedLoaded) {
			vanishedLoaded = true;
			for (String id : s().vanishedBlocks) {
				Identifier key = Identifier.tryParse(id);
				if (key != null && BuiltInRegistries.BLOCK.containsKey(key)) {
					vanished.add(BuiltInRegistries.BLOCK.getValue(key));
				}
			}
		}
		processChunkQueue(server);
		if (!s().extraTickClock && tickRateChanged) {
			setTickRate(server, 0);
		}
		if (!running()) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			if (s().extraBlockVanish) {
				tickBlockVanish(server, player);
			}
			if (s().extraWalkDecay) {
				tickWalk(player);
			}
			if (s().extraChunkMob) {
				tickChunkMob(player);
			} else {
				locks.clear();
			}
			if (s().extraSnake) {
				tickSnake(server, player);
			}
			if (s().extraTickClock && tick % 40 == 0) {
				TickClock.giveIfMissing(player);
			}
		}
		if (s().extraWalkDecay) {
			processWalkColumns(server);
		}
		if (tick % 20 == 0) {
			expireTrails(server, !s().extraSnake);
		}
	}

	// ------------------------------------------------------------------
	// 1. Block verlassen = dieser Block verschwindet überall
	// ------------------------------------------------------------------

	private void tickBlockVanish(MinecraftServer server, ServerPlayer player) {
		if (!player.onGround()) {
			return;
		}
		BlockState below = player.level().getBlockState(player.blockPosition().below());
		Block block = below.getBlock();
		if (below.isAir() || !below.getFluidState().isEmpty() || block == ignored("barrier")) {
			return;
		}
		Block previous = lastBlock.put(player.getUUID(), block);
		if (previous != null && previous != block && !vanished.contains(previous)) {
			vanished.add(previous);
			s().vanishedBlocks.add(BuiltInRegistries.BLOCK.getKey(previous).toString());
			ChallengeManager.get().save();
			ChallengeManager.broadcast(server, Component.literal(player.getName().getString() + " hat ").withStyle(ChatFormatting.GRAY)
					.append(previous.getName().copy().withStyle(ChatFormatting.RED))
					.append(Component.literal(" verlassen – alle verschwinden!").withStyle(ChatFormatting.GRAY)));
			for (ServerLevel level : server.getAllLevels()) {
				for (long chunk : loadedChunks.getOrDefault(level.dimension(), Set.of())) {
					chunkQueue.add(new Object[] {level.dimension(), chunk});
				}
			}
		}
	}

	private static Block ignored(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id));
	}

	public void onChunkLoad(ServerLevel level, LevelChunk chunk) {
		loadedChunks.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(chunk.getPos().toLong());
		if (!vanished.isEmpty() && s().extraBlockVanish) {
			chunkQueue.add(new Object[] {level.dimension(), chunk.getPos().toLong()});
		}
	}

	public void onChunkUnload(ServerLevel level, LevelChunk chunk) {
		Set<Long> set = loadedChunks.get(level.dimension());
		if (set != null) {
			set.remove(chunk.getPos().toLong());
		}
	}

	@SuppressWarnings("unchecked")
	private void processChunkQueue(MinecraftServer server) {
		if (chunkQueue.isEmpty()) {
			return;
		}
		if (vanished.isEmpty() || !s().extraBlockVanish) {
			chunkQueue.clear();
			return;
		}
		BlockState air = ignored("air").defaultBlockState();
		for (int n = 0; n < 6 && !chunkQueue.isEmpty(); n++) {
			Object[] entry = chunkQueue.poll();
			ServerLevel level = server.getLevel((ResourceKey<Level>) entry[0]);
			if (level == null) {
				continue;
			}
			long packed = (Long) entry[1];
			int cx = ChunkPos.getX(packed);
			int cz = ChunkPos.getZ(packed);
			LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
			if (chunk == null) {
				continue;
			}
			LevelChunkSection[] sections = chunk.getSections();
			for (int i = 0; i < sections.length; i++) {
				LevelChunkSection section = sections[i];
				if (section == null || section.hasOnlyAir() || !section.maybeHas(state -> vanished.contains(state.getBlock()))) {
					continue;
				}
				int baseY = chunk.getSectionYFromSectionIndex(i) * 16;
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						for (int x = 0; x < 16; x++) {
							if (vanished.contains(section.getBlockState(x, y, z).getBlock())) {
								level.setBlock(new BlockPos(cx * 16 + x, baseY + y, cz * 16 + z), air, 2 | 16);
							}
						}
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// 2. Gelaufene Blöcke + Säule darunter verschwinden
	// ------------------------------------------------------------------

	private void tickWalk(ServerPlayer player) {
		if (!player.onGround()) {
			return;
		}
		BlockPos below = player.blockPosition().below();
		if (player.level().getBlockState(below).isAir()) {
			return;
		}
		String key = player.level().dimension().identifier() + "|" + below.getX() + "|" + below.getZ();
		walkColumns.putIfAbsent(key, new long[] {tick + WALK_DELAY, below.getX(), below.getY(), below.getZ()});
	}

	private void processWalkColumns(MinecraftServer server) {
		int done = 0;
		BlockState air = ignored("air").defaultBlockState();
		Iterator<Map.Entry<String, long[]>> it = walkColumns.entrySet().iterator();
		while (it.hasNext() && done < 30) {
			Map.Entry<String, long[]> entry = it.next();
			long[] data = entry.getValue();
			if (data[0] > tick) {
				break;   // nach Zeit sortiert (Einfüge-Reihenfolge)
			}
			it.remove();
			done++;
			String dim = entry.getKey().substring(0, entry.getKey().indexOf('|'));
			ServerLevel level = null;
			for (ServerLevel candidate : server.getAllLevels()) {
				if (candidate.dimension().identifier().toString().equals(dim)) {
					level = candidate;
				}
			}
			if (level == null) {
				continue;
			}
			int x = (int) data[1];
			int z = (int) data[3];
			for (int y = (int) data[2]; y >= level.getMinY(); y--) {
				BlockPos pos = new BlockPos(x, y, z);
				if (!level.getBlockState(pos).isAir()) {
					level.setBlock(pos, air, 2 | 16);
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// 3. Neuer Chunk = Zufalls-Mob, eingesperrt bis es weg ist
	// ------------------------------------------------------------------

	private void tickChunkMob(ServerPlayer player) {
		ServerLevel level = player.level();
		ChunkLock lock = locks.get(player.getUUID());
		if (lock != null) {
			Entity mob = level.getEntity(lock.mob());
			if (!lock.level().equals(level.dimension()) || mob == null || !mob.isAlive() || mob.isRemoved()) {
				locks.remove(player.getUUID());
				lastChunk.put(player.getUUID(), ChunkPos.asLong(player.blockPosition()));
				player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("Frei! Du kannst den Chunk verlassen.").withStyle(ChatFormatting.GREEN)));
				return;
			}
			int minX = lock.chunkX() * 16;
			int minZ = lock.chunkZ() * 16;
			double x = Math.max(minX + 0.3, Math.min(minX + 15.7, player.getX()));
			double z = Math.max(minZ + 0.3, Math.min(minZ + 15.7, player.getZ()));
			if (x != player.getX() || z != player.getZ()) {
				player.teleportTo(x, player.getY(), z);
				player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("Erst das Mob loswerden!").withStyle(ChatFormatting.RED)));
			}
			return;
		}
		long chunk = ChunkPos.asLong(player.blockPosition());
		Long previous = lastChunk.put(player.getUUID(), chunk);
		if (previous == null || previous == chunk) {
			return;
		}
		Mob mob = spawnRandomMob(level, player, nearby(player));
		if (mob != null) {
			locks.put(player.getUUID(), new ChunkLock(level.dimension(), ChunkPos.getX(chunk), ChunkPos.getZ(chunk), mob.getUUID()));
			player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("Neuer Chunk: ").withStyle(ChatFormatting.GOLD)
					.append(mob.getType().getDescription().copy().withStyle(ChatFormatting.RED))
					.append(Component.literal(" – du bist eingesperrt!").withStyle(ChatFormatting.GOLD))));
		}
	}

	private BlockPos nearby(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos base = player.blockPosition();
		for (int attempt = 0; attempt < 8; attempt++) {
			BlockPos pos = base.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3);
			if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) {
				return pos;
			}
		}
		return base;
	}

	/** Zufälliges Mob (vom Huhn bis zum Wither, ohne Enderdrache) */
	Mob spawnRandomMob(ServerLevel level, Player near, BlockPos pos) {
		List<EntityType<?>> types = mobTypes(level);
		if (types.isEmpty()) {
			return null;
		}
		for (int attempt = 0; attempt < 5; attempt++) {
			EntityType<?> type = types.get(random.nextInt(types.size()));
			Entity entity = type.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (!(entity instanceof Mob mob)) {
				if (entity != null) {
					entity.discard();
				}
				continue;
			}
			mob.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
			level.addFreshEntity(mob);
			return mob;
		}
		return null;
	}

	private List<EntityType<?>> mobTypes(ServerLevel level) {
		if (mobTypes == null) {
			mobTypes = new ArrayList<>();
			for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
				String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
				if (id.equals("ender_dragon") || id.equals("player")) {
					continue;
				}
				try {
					Entity test = type.create(level, EntitySpawnReason.MOB_SUMMONED);
					if (test instanceof Mob) {
						mobTypes.add(type);
					}
					if (test != null) {
						test.discard();
					}
				} catch (RuntimeException ignored) {
					// manche Typen lassen sich nicht einfach erzeugen
				}
			}
		}
		return mobTypes;
	}

	// ------------------------------------------------------------------
	// 4. Craften = Zufalls-Mob bei jedem Spieler
	// ------------------------------------------------------------------

	public void onCraft(Player crafter) {
		if (!s().extraCraftMob || !running() || !(crafter instanceof ServerPlayer serverPlayer)) {
			return;
		}
		// Shift-Klick (viele auf einmal) zählt nur einmal
		Long last = lastCraft.put(crafter.getUUID(), tick);
		if (last != null && tick - last < 2) {
			return;
		}
		MinecraftServer server = serverPlayer.level().getServer();
		if (server == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isAlive() && !player.isSpectator()) {
				Mob mob = spawnRandomMob(player.level(), player, nearby(player));
				if (mob != null) {
					player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(crafter.getName().getString() + " hat gecraftet: ")
							.withStyle(ChatFormatting.GOLD).append(mob.getType().getDescription().copy().withStyle(ChatFormatting.RED))));
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// 8. Snake
	// ------------------------------------------------------------------

	private void tickSnake(MinecraftServer server, ServerPlayer player) {
		if (!player.onGround()) {
			return;
		}
		ServerLevel level = player.level();
		BlockPos below = player.blockPosition().below();
		BlockPos last = lastSnakePos.get(player.getUUID());
		if (below.equals(last)) {
			return;
		}
		lastSnakePos.put(player.getUUID(), below);
		String key = trailKey(level.dimension(), below);
		if (trails.containsKey(key)) {
			// Spur gekreuzt – alle sterben
			ChallengeManager.broadcast(server, Component.literal(player.getName().getString() + " hat eine Spur gekreuzt!")
					.withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
			lastSnakePos.clear();
			for (ServerPlayer other : server.getPlayerList().getPlayers()) {
				if (other.isAlive() && !other.isSpectator()) {
					other.kill(other.level());
				}
			}
			return;
		}
		BlockState state = level.getBlockState(below);
		if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
			return;
		}
		Block concrete = ignored(snakeColor(server, player) + "_concrete");
		trails.put(key, new Trail(level.dimension(), below.immutable(), state, tick + s().snakeSeconds * 20L, player.getUUID()));
		level.setBlock(below, concrete.defaultBlockState(), 2 | 16);
	}

	private String snakeColor(MinecraftServer server, ServerPlayer player) {
		List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
		players.sort((a, b) -> a.getUUID().compareTo(b.getUUID()));
		int index = Math.max(0, players.indexOf(player));
		return SNAKE_COLORS[index % SNAKE_COLORS.length];
	}

	private static String trailKey(ResourceKey<Level> level, BlockPos pos) {
		return level.identifier() + "|" + pos.asLong();
	}

	private void expireTrails(MinecraftServer server, boolean all) {
		if (trails.isEmpty()) {
			return;
		}
		Iterator<Trail> it = trails.values().iterator();
		while (it.hasNext()) {
			Trail trail = it.next();
			if (all || trail.until() <= tick) {
				ServerLevel level = server.getLevel(trail.level());
				if (level != null && level.getBlockState(trail.pos()).getBlock().toString().contains("concrete")) {
					level.setBlock(trail.pos(), trail.original(), 2 | 16);
				}
				it.remove();
			}
		}
	}

	private void restoreTrails(MinecraftServer server) {
		if (server != null) {
			expireTrails(server, true);
		}
		trails.clear();
	}

	// ------------------------------------------------------------------
	// 9. Mob töten = zufälliger permanenter Effekt
	// ------------------------------------------------------------------

	public void onEntityDeath(LivingEntity entity, DamageSource source) {
		if (!s().extraKillEffect || !running() || entity instanceof Player || !(entity instanceof Mob)
				|| MobChallenge.get().disguises().isDisguise(entity) || ArmyBattle.get().isArmyMob(entity)) {
			return;
		}
		Entity killer = source.getEntity();
		if (!(killer instanceof ServerPlayer)) {
			killer = entity.getKillCredit();
		}
		if (!(killer instanceof ServerPlayer player)) {
			return;
		}
		List<Holder<MobEffect>> all = effects();
		List<int[]> gained = gainedEffects.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
		List<int[]> options = new ArrayList<>();
		for (int i = 0; i < all.size(); i++) {
			int levels = singleLevel(all.get(i)) ? 1 : 3;
			for (int amp = 0; amp < levels; amp++) {
				boolean taken = false;
				for (int[] g : gained) {
					if (g[0] == i && g[1] == amp) {
						taken = true;
						break;
					}
				}
				if (!taken) {
					options.add(new int[] {i, amp});
				}
			}
		}
		if (options.isEmpty()) {
			player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("Du hast schon alle Effekte!").withStyle(ChatFormatting.GOLD)));
			return;
		}
		int[] pick = options.get(random.nextInt(options.size()));
		gained.add(pick);
		Holder<MobEffect> effect = all.get(pick[0]);
		player.addEffect(new MobEffectInstance(effect, -1, pick[1], false, true, true));
		player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("Neuer Effekt: ").withStyle(ChatFormatting.GOLD)
				.append(effect.value().getDisplayName().copy().withStyle(ChatFormatting.AQUA))
				.append(Component.literal(" " + roman(pick[1] + 1)).withStyle(ChatFormatting.AQUA))));
	}

	/** Nach dem Respawn bekommt man seine gesammelten Effekte zurück */
	public void onRespawn(ServerPlayer player) {
		if (!s().extraKillEffect) {
			return;
		}
		List<int[]> gained = gainedEffects.get(player.getUUID());
		if (gained == null) {
			return;
		}
		List<Holder<MobEffect>> all = effects();
		for (int[] g : gained) {
			if (g[0] < all.size()) {
				player.addEffect(new MobEffectInstance(all.get(g[0]), -1, g[1], false, true, true));
			}
		}
	}

	private List<Holder<MobEffect>> effects() {
		if (effects == null) {
			effects = new ArrayList<>();
			for (MobEffect effect : BuiltInRegistries.MOB_EFFECT) {
				if (!effect.isInstantenous()) {
					effects.add(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
				}
			}
		}
		return effects;
	}

	private static boolean singleLevel(Holder<MobEffect> effect) {
		String id = effect.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		return switch (id) {
			case "night_vision", "invisibility", "water_breathing", "fire_resistance", "glowing", "slow_falling",
					"blindness", "darkness", "nausea", "dolphins_grace" -> true;
			default -> false;
		};
	}

	private static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			default -> String.valueOf(n);
		};
	}

	// ------------------------------------------------------------------
	// 10. Mob schlagen = Platz mit zufälligem Mob tauschen
	// ------------------------------------------------------------------

	public void onDamage(LivingEntity victim, DamageSource source) {
		if (!s().extraHitSwap || !running() || !(victim instanceof Mob) || !(source.getEntity() instanceof ServerPlayer player)
				|| source.getDirectEntity() != player) {
			return;
		}
		Long last = lastSwap.get(player.getUUID());
		if (last != null && tick - last < 10) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		List<Mob> candidates = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			boolean nether = level.dimension().equals(Level.NETHER);
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof Mob mob && mob != victim && mob.isAlive() && !mob.isRemoved()
						&& !MobChallenge.get().disguises().isDisguise(mob) && !ArmyBattle.get().isArmyMob(mob)
						&& !(nether && mob.getY() >= 127)) {
					candidates.add(mob);
				}
			}
		}
		if (candidates.isEmpty()) {
			return;
		}
		lastSwap.put(player.getUUID(), tick);
		Mob other = candidates.get(random.nextInt(candidates.size()));
		ServerLevel playerLevel = player.level();
		double px = player.getX();
		double py = player.getY();
		double pz = player.getZ();
		ServerLevel mobLevel = (ServerLevel) other.level();
		double mx = other.getX();
		double my = other.getY();
		double mz = other.getZ();
		player.teleportTo(mobLevel, mx, my, mz, Set.of(), player.getYRot(), player.getXRot(), true);
		other.teleportTo(playerLevel, px, py, pz, Set.of(), other.getYRot(), other.getXRot(), true);
		player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("Getauscht mit: ").withStyle(ChatFormatting.GOLD)
				.append(other.getType().getDescription().copy().withStyle(ChatFormatting.AQUA))));
	}

	// ------------------------------------------------------------------
	// 11. Tick-Uhr
	// ------------------------------------------------------------------

	public InteractionResult onUseItem(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!TickClock.isClock(stack) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		if (!s().extraTickClock) {
			return InteractionResult.FAIL;
		}
		MinecraftServer server = serverPlayer.level().getServer();
		if (server != null) {
			setTickRate(server, (tickRateIndex + 1) % TICK_RATES.length);
			ChallengeManager.broadcast(server, Component.literal(player.getName().getString() + " stellt die Zeit auf: ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(TICK_NAMES[tickRateIndex]).withStyle(ChatFormatting.AQUA)));
		}
		return InteractionResult.SUCCESS;
	}

	private void setTickRate(MinecraftServer server, int index) {
		if (server == null) {
			return;
		}
		if (index == tickRateIndex && !tickRateChanged && index == 0) {
			return;
		}
		tickRateIndex = index;
		tickRateChanged = index != 0;
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "tick rate " + TICK_RATES[index]);
	}
}
