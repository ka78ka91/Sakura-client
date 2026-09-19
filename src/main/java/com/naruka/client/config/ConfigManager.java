package com.naruka.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads and writes {@code config/naruka.json}.
 *
 * <p>The file is written on every mutation through {@link #save()}; a corrupt or unreadable file never
 * throws into the game loop, it just falls back to defaults so a bad config cannot make the client
 * unlaunchable.</p>
 */
public final class ConfigManager {

	private static final Logger LOGGER = LoggerFactory.getLogger("naruka/config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("naruka.json");

	private static NarukaConfig config = new NarukaConfig();

	private ConfigManager() {
	}

	public static NarukaConfig get() {
		return config;
	}

	public static Path getPath() {
		return CONFIG_PATH;
	}

	public static void load() {
		if (!Files.exists(CONFIG_PATH)) {
			config = new NarukaConfig();
			save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
			NarukaConfig loaded = GSON.fromJson(reader, NarukaConfig.class);
			config = loaded != null ? loaded : new NarukaConfig();
		} catch (IOException | RuntimeException exception) {
			LOGGER.warn("Could not read {}, falling back to defaults", CONFIG_PATH, exception);
			config = new NarukaConfig();
		}
	}

	public static void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());

			try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException | RuntimeException exception) {
			LOGGER.warn("Could not write {}", CONFIG_PATH, exception);
		}
	}

	/** Restores every field to its default value and persists the result. */
	public static void reset() {
		config = new NarukaConfig();
		save();
	}
}
