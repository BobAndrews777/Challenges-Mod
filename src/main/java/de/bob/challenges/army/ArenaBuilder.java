package de.bob.challenges.army;

import java.util.ArrayList;
import java.util.List;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Baut die Arena in einer Team-Welt – entweder die eingebaute Halle oder eine eigene Map
 * (mit dem Konstruktionsblock gespeicherte Struktur). In eigenen Maps markieren:
 * Lapislazuliblock = Mob-Spawnpunkt, Diamantblock = Start der Spieler.
 */
final class ArenaBuilder {
	static final String MOB_MARKER = "lapis_block";
	static final String PLAYER_MARKER = "diamond_block";

	/** Fertige Arena: Bereich und Punkte */
	record Arena(BlockPos min, BlockPos max, List<BlockPos> mobSpawns, List<BlockPos> playerSpawns) {
		boolean contains(double x, double y, double z) {
			return x >= min.getX() - 2 && x <= max.getX() + 3 && y >= min.getY() - 2 && y <= max.getY() + 3
					&& z >= min.getZ() - 2 && z <= max.getZ() + 3;
		}

		BlockPos center() {
			return new BlockPos((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2, (min.getZ() + max.getZ()) / 2);
		}
	}

	private ArenaBuilder() {
	}

	/** Eigene Map, falls eingestellt und vorhanden – sonst die eingebaute Halle. */
	static Arena build(ServerLevel level, String structure, List<String> problems) {
		if (structure != null && !structure.isBlank()) {
			Arena arena = place(level, structure, problems);
			if (arena != null) {
				return arena;
			}
			problems.add("Es wird stattdessen die Standard-Arena benutzt.");
		}
		return buildDefault(level);
	}

	private static Arena place(ServerLevel level, String structure, List<String> problems) {
		Arena bundled = placeBundled(level, structure.trim().toLowerCase(), problems);
		if (bundled != null) {
			return bundled;
		}
		Identifier id = Identifier.tryParse(structure.contains(":") ? structure : "minecraft:" + structure);
		if (id == null) {
			problems.add("Ungültiger Name für die Arena-Map: " + structure);
			return null;
		}
		Optional<StructureTemplate> template = Optional.empty();
		String error = null;
		try {
			template = level.getStructureTemplateManager().get(id);
		} catch (RuntimeException e) {
			error = e.getMessage();
		}
		if (template.isEmpty()) {
			problems.add("Arena-Map „" + id + "“ nicht gefunden" + (error != null ? " (Fehler: " + error + ")" : "") + ".");
			return null;
		}
		Vec3i size = template.get().getSize();
		if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
			problems.add("Arena-Map „" + id + "“ ist leer.");
			return null;
		}
		int y = Math.max(level.getMinY() + 1, Math.min(250, level.getMaxY() - size.getY() - 2));
		BlockPos min = new BlockPos(-size.getX() / 2, y, -size.getZ() / 2);
		StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(true);
		template.get().placeInWorld(level, min, min, settings, level.getRandom(), 2);
		BlockPos max = min.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);

		List<BlockPos> mobSpawns = new ArrayList<>();
		List<BlockPos> playerSpawns = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			String block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
			if (block.equals(MOB_MARKER)) {
				mobSpawns.add(pos.above().immutable());
			} else if (block.equals(PLAYER_MARKER)) {
				playerSpawns.add(pos.above().immutable());
			}
		}
		if (mobSpawns.isEmpty()) {
			problems.add("In der Arena-Map fehlt ein Lapislazuliblock als Mob-Spawnpunkt – Mobs spawnen jetzt in der Mitte.");
			mobSpawns.add(surface(level, min, max));
		}
		if (playerSpawns.isEmpty()) {
			problems.add("In der Arena-Map fehlt ein Diamantblock als Spieler-Start – ihr startet in der Mitte.");
			playerSpawns.add(surface(level, min, max));
		}
		return new Arena(min, max, mobSpawns, playerSpawns);
	}

	/**
	 * In der Mod mitgelieferte Arena (resources/arenas/<name>.txt.gz) setzen.
	 * Format: "size x y z", "palette n", n Zeilen Blockzustand, dann "x y z index" pro Block (Luft fehlt).
	 */
	private static Arena placeBundled(ServerLevel level, String name, List<String> problems) {
		String file = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
		InputStream raw = ArenaBuilder.class.getResourceAsStream("/arenas/" + file + ".txt.gz");
		if (raw == null) {
			return null;
		}
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8))) {
			String[] size = reader.readLine().split(" ");
			int sx = Integer.parseInt(size[1]);
			int sy = Integer.parseInt(size[2]);
			int sz = Integer.parseInt(size[3]);
			int count = Integer.parseInt(reader.readLine().split(" ")[1]);
			BlockState[] palette = new BlockState[count];
			for (int i = 0; i < count; i++) {
				palette[i] = parseState(reader.readLine());
			}
			int y0 = Math.max(level.getMinY() + 1, Math.min(250, level.getMaxY() - sy - 2));
			BlockPos min = new BlockPos(-sx / 2, y0, -sz / 2);
			BlockPos max = min.offset(sx - 1, sy - 1, sz - 1);
			// Bereich leeren (z. B. Reste einer anderen Arena)
			BlockState air = state("air");
			for (BlockPos pos : BlockPos.betweenClosed(min.offset(-2, -2, -2), max.offset(2, 4, 2))) {
				if (!level.getBlockState(pos).isAir()) {
					level.setBlock(pos, air, 2);
				}
			}
			List<BlockPos> mobSpawns = new ArrayList<>();
			List<BlockPos> playerSpawns = new ArrayList<>();
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				String[] parts = line.split(" ");
				BlockPos pos = min.offset(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
				BlockState state = palette[Integer.parseInt(parts[3])];
				level.setBlock(pos, state, 2);
				String block = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
				if (block.equals(MOB_MARKER)) {
					mobSpawns.add(pos.above());
				} else if (block.equals(PLAYER_MARKER)) {
					playerSpawns.add(pos.above());
				}
			}
			if (mobSpawns.isEmpty()) {
				mobSpawns.add(surface(level, min, max));
			}
			if (playerSpawns.isEmpty()) {
				playerSpawns.add(surface(level, min, max));
			}
			return new Arena(min, max, mobSpawns, playerSpawns);
		} catch (Exception e) {
			problems.add("Arena „" + file + "“ konnte nicht geladen werden: " + e.getClass().getSimpleName() + " " + e.getMessage());
			return null;
		}
	}

	/** "minecraft:oak_stairs[facing=east,half=bottom]" → Blockzustand (unbekannte Eigenschaften werden ignoriert) */
	private static BlockState parseState(String text) {
		String name = text;
		String props = "";
		int bracket = text.indexOf('[');
		if (bracket >= 0) {
			name = text.substring(0, bracket);
			props = text.substring(bracket + 1, text.length() - 1);
		}
		Identifier id = Identifier.tryParse(name);
		if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
			return state("air");
		}
		Block block = BuiltInRegistries.BLOCK.getValue(id);
		BlockState state = block.defaultBlockState();
		if (!props.isEmpty()) {
			for (String pair : props.split(",")) {
				String[] kv = pair.split("=", 2);
				if (kv.length == 2) {
					Property<?> property = block.getStateDefinition().getProperty(kv[0]);
					if (property != null) {
						state = withValue(state, property, kv[1]);
					}
				}
			}
		}
		return state;
	}

	private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property, String value) {
		return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
	}

	/** Arena nach dem Kampf wieder entfernen (alles zu Luft). */
	static void remove(ServerLevel level, Arena arena) {
		BlockState air = state("air");
		for (BlockPos pos : BlockPos.betweenClosed(arena.min().offset(-2, -2, -2), arena.max().offset(2, 4, 2))) {
			if (!level.getBlockState(pos).isAir()) {
				level.setBlock(pos, air, 2 | 16);
			}
		}
	}

	/** Erster freier Platz von oben in der Mitte der Map */
	private static BlockPos surface(ServerLevel level, BlockPos min, BlockPos max) {
		int x = (min.getX() + max.getX()) / 2;
		int z = (min.getZ() + max.getZ()) / 2;
		for (int y = max.getY(); y > min.getY(); y--) {
			BlockPos pos = new BlockPos(x, y, z);
			if (!level.getBlockState(pos.below()).isAir() && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) {
				return pos;
			}
		}
		return new BlockPos(x, max.getY(), z);
	}

	/** Die eingebaute Halle hoch in der Luft über 0/0 */
	private static Arena buildDefault(ServerLevel level) {
		int half = 16;
		int floor = 280;
		int roof = floor + 17;

		BlockState air = state("air");
		BlockState floorBlock = state("smooth_stone");
		BlockState wall = state("stone_bricks");
		BlockState light = state("sea_lantern");
		BlockState glass = state("tinted_glass");
		BlockState barrier = state("barrier");
		BlockState mark = state(MOB_MARKER);

		for (int x = -half - 1; x <= half + 1; x++) {
			for (int z = -half - 1; z <= half + 1; z++) {
				boolean edge = Math.abs(x) == half + 1 || Math.abs(z) == half + 1;
				for (int y = floor - 1; y <= roof; y++) {
					BlockState state;
					if (y == floor - 1) {
						state = wall;
					} else if (y == floor) {
						state = edge ? wall : (x % 6 == 0 && z % 6 == 0 ? light : floorBlock);
					} else if (y == roof) {
						state = x % 4 == 0 && z % 4 == 0 ? light : wall;
					} else if (edge) {
						int height = y - floor;
						state = height == 2 && (x + z) % 4 == 0 ? light : (height >= 5 && height <= 7 ? glass : wall);
					} else {
						state = air;
					}
					level.setBlock(new BlockPos(x, y, z), state, 2);
				}
				level.setBlock(new BlockPos(x, roof + 1, z), barrier, 2);
			}
		}
		int[][] points = {{-13, -13}, {13, -13}, {13, 13}, {-13, 13}, {0, -14}, {14, 0}, {0, 14}, {-14, 0}};
		List<BlockPos> mobSpawns = new ArrayList<>();
		for (int[] point : points) {
			level.setBlock(new BlockPos(point[0], floor, point[1]), mark, 2);
			mobSpawns.add(new BlockPos(point[0], floor + 1, point[1]));
		}
		List<BlockPos> playerSpawns = new ArrayList<>();
		for (int x = -3; x <= 3; x++) {
			playerSpawns.add(new BlockPos(x, floor + 1, 0));
		}
		return new Arena(new BlockPos(-half, floor, -half), new BlockPos(half, roof, half), mobSpawns, playerSpawns);
	}

	private static BlockState state(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)).defaultBlockState();
	}
}
