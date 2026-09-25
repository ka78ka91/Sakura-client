package com.sakura.client.render;

import com.sakura.client.SakuraClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.resource.PackVersion;
import net.minecraft.resource.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Turns a font file the player dropped in themselves into a resource pack.
 *
 * <p>1.21.11 has no clean way to register a font at runtime, so the only route that works is a resource pack.
 * The obvious approach — bundling a TTF inside the mod jar — was rejected on purpose. MiSans, the font this was
 * written for, is free to use but its licence explicitly forbids redistributing the font software itself, so
 * shipping it in this repository would be a licence violation, and shipping a differently-licensed font would
 * silently answer a question the player was asked. Generating the pack on the player's own machine from a file
 * they supply distributes nothing and lets them change fonts by replacing one file.</p>
 *
 * <h2>How it works</h2>
 *
 * <p>On client start-up this looks for a {@code .ttf} or {@code .otf} in {@code config/sakura/font/} and writes
 * an ordinary resource pack among the player's others: {@code resourcepacks/sakura_font/} holding a
 * {@code pack.mcmeta}, a {@code default.json} that lists the font first and vanilla's unifont after it, and a
 * copy of the font. The pack is rebuilt whenever the source font is newer than the copy it produced, so
 * replacing the font takes effect without deleting anything by hand.</p>
 *
 * <p>The pack is <em>not</em> enabled automatically. It is a normal pack, it appears in the Resource Packs
 * screen like any other, and switching it on is the player's decision — which also avoids this client reaching
 * into a screen it does not own.</p>
 */
public final class FontPack {

	private static final Logger LOGGER = LoggerFactory.getLogger("sakura/font");

	/** Folder under the config directory the player drops a font into. */
	private static final String SOURCE_DIR = "font";

	/** Name of the generated pack, which is also its folder name under {@code resourcepacks/}. */
	private static final String PACK_NAME = "sakura_font";

	/** Description shown in the Resource Packs screen. */
	private static final String PACK_DESCRIPTION = "Sakura Client font";

	private FontPack() {
	}

	/** Builds the pack if a font is present. Called once from the client initialiser. */
	public static void install() {
		try {
			Path configDir = FabricLoader.getInstance().getConfigDir();
			Path modDir = configDir.resolve(SakuraClient.MOD_ID);
			Path source = modDir.resolve(SOURCE_DIR);

			Files.createDirectories(source);

			Path font = findFont(source);

			if (font == null) {
				LOGGER.info("No font file in {}; drop a .ttf there and restart to get a '{}' resource pack",
						source, PACK_NAME);
				return;
			}

			// The resource pack folder sits beside the config folder, which is the game directory in both a real
			// install and the development run directory.
			Path gameDir = configDir.getParent() == null ? configDir : configDir.getParent();
			Path pack = gameDir.resolve("resourcepacks").resolve(PACK_NAME);
			Path installed = pack.resolve("assets/minecraft/font").resolve(font.getFileName());

			if (isUpToDate(pack, font, installed)) {
				LOGGER.info("Font pack is already up to date from {}", font.getFileName());
				return;
			}

			writePack(pack, font, installed);
			LOGGER.info("Built the '{}' resource pack from {}; switch it on in Options > Resource Packs",
					PACK_NAME, font.getFileName());
		} catch (IOException | RuntimeException exception) {
			// A failed font pack must never stop the client: the player can still play with the vanilla font, and
			// the log says why it did not happen.
			LOGGER.warn("Could not build the font resource pack: {}", exception.getMessage());
		}
	}

	/** @return whether the generated pack is already newer than the font it came from */
	private static boolean isUpToDate(Path pack, Path font, Path installed) throws IOException {
		if (!Files.exists(installed) || !Files.exists(pack.resolve("pack.mcmeta"))
				|| !Files.exists(pack.resolve("assets/minecraft/font/default.json"))) {
			return false;
		}

		return Files.getLastModifiedTime(installed).compareTo(Files.getLastModifiedTime(font)) >= 0;
	}

	/** @return the first {@code .ttf} or {@code .otf} directly inside {@code dir}, or {@code null} when there is none */
	private static Path findFont(Path dir) throws IOException {
		try (Stream<Path> entries = Files.list(dir)) {
			List<Path> fonts = entries
					.filter(Files::isRegularFile)
					.filter(FontPack::looksLikeFont)
					.sorted()
					.toList();

			return fonts.isEmpty() ? null : fonts.get(0);
		}
	}

	private static boolean looksLikeFont(Path path) {
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return name.endsWith(".ttf") || name.endsWith(".otf");
	}

	private static void writePack(Path pack, Path font, Path installed) throws IOException {
		Path fontDir = pack.resolve("assets/minecraft/font");
		Files.createDirectories(fontDir);
		Files.copy(font, installed, StandardCopyOption.REPLACE_EXISTING);

		Files.writeString(pack.resolve("pack.mcmeta"), packMeta(), StandardCharsets.UTF_8);
		Files.writeString(fontDir.resolve("default.json"), fontProvider(font.getFileName().toString()),
				StandardCharsets.UTF_8);
	}

	/**
	 * @return the pack description
	 *
	 * <p>The versions are read from the running game rather than written as literals, because a literal would make
	 * the pack silently stop loading after a Minecraft update.</p>
	 *
	 * <p>Both spellings of the format are written. This version of the game moved to a {@code min_format} /
	 * {@code max_format} pair and rejects a pack that declares a format newer than 64 without them — the message
	 * is literally "Pack declares support for version newer than 64, but is missing mandatory fields min_format
	 * and max_format" — while older versions only understand the single {@code pack_format}. Writing all three
	 * costs nothing and means the same generated pack keeps working if the player moves between versions.</p>
	 *
	 * <p>The lower bound is left at the same value as the upper one: this pack targets exactly the version that
	 * generated it, and claiming to support older formats it has never been tested against is how a pack ends up
	 * quietly mis-rendering instead of being reported as incompatible.</p>
	 */
	private static String packMeta() {
		PackVersion version = currentVersion();
		int major = version.major();
		int minor = version.minor();

		return """
				{
					"pack": {
						"pack_format": %d,
						"min_format": [%d, %d],
						"max_format": [%d, %d],
						"description": "%s"
					}
				}
				""".formatted(major, major, minor, major, minor, PACK_DESCRIPTION);
	}

	/** @return the client resource pack version this build speaks, or {@code 0} before the game version exists */
	private static PackVersion currentVersion() {
		if (SharedConstants.getGameVersion() == null) {
			return PackVersion.of(0);
		}

		return SharedConstants.getGameVersion().packVersion(ResourceType.CLIENT_RESOURCES);
	}

	/**
	 * @return a font definition that puts the custom face first and vanilla's unifont after it
	 *
	 * <p>The unifont entry is what keeps CJK working when the custom font does not cover it: a glyph missing from
	 * the first provider is looked up in the next one, so a Latin-only font cannot turn Chinese text into
	 * missing-glyph boxes. This file replaces vanilla's default font definition outright, so the reference has to
	 * be written out here rather than inherited.</p>
	 *
	 * <p>{@code size}, {@code oversample} and {@code shift} are the values the vanilla font uses; changing them
	 * is what makes a TTF look too large, too blurry or vertically offset, so they are left alone.</p>
	 */
	private static String fontProvider(String fileName) {
		return """
				{
					"providers": [
						{
							"type": "ttf",
							"file": "minecraft:font/%s",
							"size": 11.0,
							"oversample": 2.0,
							"shift": [0.0, 0.0]
						},
						{
							"type": "reference",
							"id": "minecraft:include/unifont"
						}
					]
				}
				""".formatted(fileName);
	}
}
