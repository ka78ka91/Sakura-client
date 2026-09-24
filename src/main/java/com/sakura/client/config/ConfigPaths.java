package com.sakura.client.config;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves where Sakura keeps its files, and moves anything left behind by the old single-directory layout.
 *
 * <p>With version isolation on, launchers run the game with its working directory pointed at
 * {@code .minecraft/versions/&lt;name&gt;/}, so the game directory itself is the version folder and Sakura's
 * files belong in {@code versions/&lt;name&gt;/sakura/}. That shape is recognised by the game directory's
 * parent being named {@code versions}. Everywhere else —isolation off, the vanilla launcher, the dev
 * environment's {@code run/} —the files fall back to {@code config/sakura/} inside the game directory, which
 * is where the previous layout already kept them, so nothing moves on those installs.</p>
 *
 * <p>The fallback is deliberately conservative: guessing wrong would scatter a player's config across two
 * locations, while falling back merely keeps a version-isolated install in the shared folder it used before.</p>
 */
public final class ConfigPaths {

	private static final Logger LOGGER = LoggerFactory.getLogger("sakura/paths");

	private static final String ROOT_FOLDER = "sakura";
	private static final String MEDIA_FOLDER = "media";
	private static final String FONTS_FOLDER = "fonts";
	private static final String PROFILES_FOLDER = "profiles";
	private static final String BACKUP_FOLDER = "backup";
	private static final String LOGS_FOLDER = "logs";
	/** The game-dir subfolder the pre-isolation layout stored everything in. */
	private static final String LEGACY_CONFIG_FOLDER = "config";
	private static final String LEGACY_MEDIA_FOLDER = "sakura";

	private static Path baseDir;

	private ConfigPaths() {
	}

	/** @return the folder holding sakura.json and Sakura's subfolders, created on first call */
	public static Path getBaseDir() {
		if (baseDir == null) {
			baseDir = resolveBaseDir();
			mkdirs(baseDir);
			mkdirs(getProfilesDir());
			mkdirs(getBackupDir());
			mkdirs(getMediaDir());
			mkdirs(getFontsDir());
			mkdirs(getLogsDir());
		}

		return baseDir;
	}

	/** Named config snapshots, one file per profile. */
	public static Path getProfilesDir() {
		return getBaseDir().resolve(PROFILES_FOLDER);
	}

	/** Rolling backups of the live config, newest kept. */
	public static Path getBackupDir() {
		return getBaseDir().resolve(BACKUP_FOLDER);
	}

	/** Media-bridge runtime files: the PowerShell script, cover art and the command pipe. */
	public static Path getMediaDir() {
		return getBaseDir().resolve(MEDIA_FOLDER);
	}

	/** Optional TTF files the custom font can load. */
	public static Path getFontsDir() {
		return getBaseDir().resolve(FONTS_FOLDER);
	}

	/** Reserved for per-session logs. */
	public static Path getLogsDir() {
		return getBaseDir().resolve(LOGS_FOLDER);
	}

	/** @return the game dir's {@code config/} folder, where the pre-isolation layout kept everything */
	static Path legacyConfigDir() {
		return gameDir().resolve(LEGACY_CONFIG_FOLDER);
	}

	/**
	 * Copies one media-bridge file out of the old {@code config/sakura/} layout when it only exists there.
	 *
	 * <p>Migration is copy-only: the original is left in place so a half-finished move can never destroy the
	 * only copy of a file.</p>
	 *
	 * @param fileName the file's plain name, e.g. {@code media-command.txt}
	 */
	public static void migrateLegacyMediaFile(String fileName) {
		Path legacy = legacyConfigDir().resolve(LEGACY_MEDIA_FOLDER).resolve(fileName);
		Path target = getMediaDir().resolve(fileName);
		copyIfMissing(legacy, target);
	}

	private static Path resolveBaseDir() {
		Path gameDir = gameDir();
		Path parent = gameDir.getParent();

		// Version isolation: the launcher points the game directory at versions/<name>/ itself.
		if (parent != null && parent.getFileName() != null
				&& "versions".equals(parent.getFileName().toString())) {
			return gameDir.resolve(ROOT_FOLDER);
		}

		return gameDir.resolve(LEGACY_CONFIG_FOLDER).resolve(ROOT_FOLDER);
	}

	private static Path gameDir() {
		return FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
	}

	private static void mkdirs(Path dir) {
		try {
			Files.createDirectories(dir);
		} catch (IOException exception) {
			LOGGER.warn("Could not create {}", dir, exception);
		}
	}

	private static void copyIfMissing(Path from, Path to) {
		if (!Files.isRegularFile(from) || Files.exists(to)) {
			return;
		}

		try {
			Files.createDirectories(to.getParent());
			Files.copy(from, to);
			LOGGER.info("Migrated {} to {}", from, to);
		} catch (IOException exception) {
			// Losing a migrated media file is cosmetic; the bridge re-creates the script and art on demand.
			LOGGER.warn("Could not migrate {} to {}: {}", from, to, exception.getMessage());
		}
	}
}
