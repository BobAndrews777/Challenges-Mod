package de.bob.challenges;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Welt-Reset: /challenge reset confirm legt eine Markierungs-Datei an und stoppt den Server.
 * Beim nächsten Start wird die Welt gelöscht, bevor Minecraft sie lädt – es entsteht eine neue.
 */
public final class WorldReset {
	private WorldReset() {
	}

	private static Path gameDir() {
		return FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
	}

	private static Path flagFile() {
		return gameDir().resolve("challenges-reset.flag");
	}

	public static boolean isSupported() {
		return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
	}

	public static boolean scheduleReset() {
		try {
			Files.writeString(flagFile(), "Diese Datei sorgt dafür, dass beim nächsten Start eine neue Welt erstellt wird.");
			return true;
		} catch (IOException e) {
			ChallengesMod.LOGGER.error("Konnte Reset-Datei nicht anlegen", e);
			return false;
		}
	}

	/**
	 * Wird ganz am Anfang beim Laden der Mod aufgerufen (vor dem Laden der Welt).
	 *
	 * @return true, wenn die Welt gelöscht wurde
	 */
	public static boolean runPendingReset() {
		if (!isSupported() || !Files.exists(flagFile())) {
			return false;
		}
		try {
			Path world = gameDir().resolve(levelName()).normalize();
			if (!world.startsWith(gameDir()) || world.equals(gameDir())) {
				ChallengesMod.LOGGER.error("Ungültiger Weltordner {}, Reset abgebrochen", world);
				return false;
			}
			if (Files.exists(world)) {
				try (Stream<Path> paths = Files.walk(world)) {
					for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
						Files.delete(path);
					}
				}
				ChallengesMod.LOGGER.info("Welt {} gelöscht – es wird eine neue erstellt", world);
			}
			Files.deleteIfExists(flagFile());
			return true;
		} catch (IOException e) {
			ChallengesMod.LOGGER.error("Welt-Reset fehlgeschlagen", e);
			return false;
		}
	}

	private static String levelName() {
		Path properties = gameDir().resolve("server.properties");
		if (Files.exists(properties)) {
			try (Reader reader = Files.newBufferedReader(properties)) {
				Properties props = new Properties();
				props.load(reader);
				String name = props.getProperty("level-name", "world").trim();
				if (!name.isEmpty()) {
					return name;
				}
			} catch (IOException e) {
				ChallengesMod.LOGGER.warn("Konnte server.properties nicht lesen", e);
			}
		}
		return "world";
	}
}
