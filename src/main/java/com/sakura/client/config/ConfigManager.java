package com.sakura.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Reads and writes the active Sakura config, and keeps the profile and backup folders around it healthy.
 *
 * <p>The live file is always {@code <base>/sakura.json}; {@code profiles/<name>.json} hold named snapshots
 * and {@code settings.json} records which one is active. Every save first copies the previous file into
 * {@code backup/}, keeping the ten newest, so a bad write is always one file away from recoverable. A file
 * that fails to parse is quarantined into {@code backup/} and replaced with defaults instead of being left
 * on disk to fail again on every launch.</p>
 *
 * <p>Reading never swaps the live {@link SakuraConfig} instance: files are parsed into a temporary object
 * and their fields are copied in, so a holder of {@link #get()} can never end up writing into a stale
 * object the persistence layer no longer sees.</p>
 */
public final class ConfigManager {

	private static final Logger LOGGER = LoggerFactory.getLogger("sakura/config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);
	private static final int BACKUPS_TO_KEEP = 10;
	private static final String LIVE_FILE = "sakura.json";
	private static final String SETTINGS_FILE = "settings.json";
	private static final String DEFAULT_PROFILE = "default";

	/** The single record settings.json holds. */
	private static final class ProfileSettings {
		public String activeProfile = DEFAULT_PROFILE;
	}

	private static SakuraConfig config = new SakuraConfig();
	private static String activeProfile = DEFAULT_PROFILE;

	private ConfigManager() {
	}

	public static SakuraConfig get() {
		return config;
	}

	/** @return the live config file, shown on the Config page */
	public static Path getPath() {
		return livePath();
	}

	public static String getActiveProfile() {
		return activeProfile;
	}

	public static void load() {
		readActiveProfile();
		Path live = livePath();

		if (!Files.exists(live)) {
			// First launch on this install: adopt the pre-3.0 config if there is one, start from defaults
			// without a sound when there is not.
			if (!migrateLegacyConfig(live)) {
				config.copyFrom(new SakuraConfig());
				save();
				return;
			}
		}

		try (Reader reader = Files.newBufferedReader(live)) {
			SakuraConfig loaded = GSON.fromJson(reader, SakuraConfig.class);

			if (loaded != null) {
				config.copyFrom(loaded);
			}
		} catch (IOException | RuntimeException exception) {
			LOGGER.warn("Could not read {}, quarantining it and writing defaults", live, exception);
			quarantine(live);
			config.copyFrom(new SakuraConfig());
			save();
		}
	}

	public static void save() {
		Path live = livePath();

		try {
			Files.createDirectories(live.getParent());
			backupExisting(live);

			try (Writer writer = Files.newBufferedWriter(live)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException | RuntimeException exception) {
			LOGGER.warn("Could not write {}", live, exception);
		}
	}

	/** Restores every field of the live config to its default value and persists the result. */
	public static void reset() {
		config.copyFrom(new SakuraConfig());
		save();
	}

	// ------------------------------------------------------------------ profiles

	/**
	 * @return the profile names on disk, sorted; {@code default} is always listed even before its first
	 * snapshot exists
	 */
	public static List<String> listProfiles() {
		List<String> names = new ArrayList<>();

		try (Stream<Path> entries = Files.list(ConfigPaths.getProfilesDir())) {
			entries.filter(path -> path.getFileName() != null
							&& path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
					.map(path -> stripJson(path.getFileName().toString()))
					.filter(name -> !name.isBlank())
					.sorted()
					.forEach(names::add);
		} catch (IOException exception) {
			LOGGER.warn("Could not list profiles: {}", exception.getMessage());
		}

		if (!names.contains(DEFAULT_PROFILE)) {
			names.add(0, DEFAULT_PROFILE);
		}

		return names;
	}

	/**
	 * Switches the active profile: the live config is parked under the outgoing profile's name, then the
	 * target profile's file becomes the live config. A profile with no file yet starts from defaults.
	 *
	 * @return true when the switch happened; the caller must re-apply module state, module settings and HUD
	 * positions afterwards either way
	 */
	public static boolean switchProfile(String name) {
		if (name == null || name.isBlank() || name.equals(activeProfile)) {
			return false;
		}

		snapshotProfile(activeProfile);
		activeProfile = sanitize(name);
		writeActiveProfile();

		Path snapshot = profilePath(activeProfile);

		if (Files.isRegularFile(snapshot)) {
			try (Reader reader = Files.newBufferedReader(snapshot)) {
				SakuraConfig loaded = GSON.fromJson(reader, SakuraConfig.class);

				if (loaded != null) {
					config.copyFrom(loaded);
				}
			} catch (IOException | RuntimeException exception) {
				LOGGER.warn("Could not read profile {}, starting it from defaults", activeProfile, exception);
				config.copyFrom(new SakuraConfig());
			}
		} else {
			config.copyFrom(new SakuraConfig());
		}

		save();
		return true;
	}

	/** Writes the live config under a profile's name. */
	private static void snapshotProfile(String name) {
		Path target = profilePath(name);

		try {
			Files.createDirectories(target.getParent());

			try (Writer writer = Files.newBufferedWriter(target)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException | RuntimeException exception) {
			LOGGER.warn("Could not snapshot profile {}: {}", name, exception.getMessage());
		}
	}

	private static void readActiveProfile() {
		Path settings = ConfigPaths.getBaseDir().resolve(SETTINGS_FILE);

		if (!Files.isRegularFile(settings)) {
			activeProfile = DEFAULT_PROFILE;
			return;
		}

		try (Reader reader = Files.newBufferedReader(settings)) {
			ProfileSettings read = GSON.fromJson(reader, ProfileSettings.class);
			activeProfile = read != null && read.activeProfile != null && !read.activeProfile.isBlank()
					? read.activeProfile : DEFAULT_PROFILE;
		} catch (IOException | RuntimeException exception) {
			LOGGER.warn("Could not read {}, using the default profile", settings, exception);
			activeProfile = DEFAULT_PROFILE;
		}
	}

	private static void writeActiveProfile() {
		Path settings = ConfigPaths.getBaseDir().resolve(SETTINGS_FILE);
		ProfileSettings payload = new ProfileSettings();
		payload.activeProfile = activeProfile;

		try {
			Files.createDirectories(settings.getParent());

			try (Writer writer = Files.newBufferedWriter(settings)) {
				GSON.toJson(payload, writer);
			}
		} catch (IOException | RuntimeException exception) {
			LOGGER.warn("Could not write {}", settings, exception);
		}
	}

	private static Path profilePath(String name) {
		return ConfigPaths.getProfilesDir().resolve(sanitize(name) + ".json");
	}

	/** Keeps a profile name a plain file name: separators and reserved characters become underscores. */
	private static String sanitize(String name) {
		String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
		return cleaned.isEmpty() ? DEFAULT_PROFILE : cleaned;
	}

	private static String stripJson(String fileName) {
		return fileName.substring(0, fileName.length() - ".json".length());
	}

	// ------------------------------------------------------------------- backup

	/** Copies the previous live file aside before it is overwritten. */
	private static void backupExisting(Path live) {
		if (!Files.isRegularFile(live)) {
			return;
		}

		String stamp = LocalDateTime.now().format(STAMP);
		Path backup = ConfigPaths.getBackupDir().resolve("sakura-" + stamp + ".json");

		try {
			Files.copy(live, backup, StandardCopyOption.REPLACE_EXISTING);
			pruneBackups();
		} catch (IOException exception) {
			LOGGER.warn("Could not back the config up: {}", exception.getMessage());
		}
	}

	private static void pruneBackups() {
		List<Path> backups = new ArrayList<>();

		try (Stream<Path> entries = Files.list(ConfigPaths.getBackupDir())) {
			entries.filter(path -> {
						String name = path.getFileName() != null ? path.getFileName().toString() : "";
						return name.startsWith("sakura-") && name.endsWith(".json");
					})
					.forEach(backups::add);
		} catch (IOException exception) {
			return;
		}

		// The stamp sorts lexicographically, so name order is age order.
		backups.sort(Comparator.comparing(path -> path.getFileName().toString()));

		for (int index = 0; index < backups.size() - BACKUPS_TO_KEEP; index++) {
			try {
				Files.deleteIfExists(backups.get(index));
			} catch (IOException exception) {
				return;
			}
		}
	}

	/** Parks an unreadable live file under a distinct name so the same bytes are never parsed twice. */
	private static void quarantine(Path live) {
		String stamp = LocalDateTime.now().format(STAMP);
		Path target = ConfigPaths.getBackupDir().resolve("sakura.json.corrupt-" + stamp);

		try {
			Files.copy(live, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException exception) {
			LOGGER.warn("Could not quarantine the broken config: {}", exception.getMessage());
		}
	}

	/** @return true when a legacy config was found and copied into the live file */
	private static boolean migrateLegacyConfig(Path live) {
		Path legacy = ConfigPaths.legacyConfigDir().resolve(LIVE_FILE);

		if (!Files.isRegularFile(legacy)) {
			return false;
		}

		try {
			Files.createDirectories(live.getParent());
			Files.copy(legacy, live);
			LOGGER.info("Adopted the legacy config at {} as {}", legacy, live);
			return true;
		} catch (IOException exception) {
			LOGGER.warn("Could not adopt the legacy config {}: {}", legacy, exception.getMessage());
			return false;
		}
	}

	private static Path livePath() {
		return ConfigPaths.getBaseDir().resolve(LIVE_FILE);
	}
}
