package com.sakura.client.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sakura.client.config.ConfigPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Real now-playing data on Windows, read from the operating system's media session.
 *
 * <p>Windows exposes every media app through the System Media Transport Controls (SMTC) — the API behind
 * the media keys and the volume-flyout panel. Whatever registers there (Spotify, NetEase Cloud Music,
 * QQ Music, a browser tab, foobar2000, VLC) becomes readable, including title, artist, album, playback
 * status, position and cover art, and it can also be controlled from here.</p>
 *
 * <h2>Why a PowerShell bridge</h2>
 *
 * <p>SMTC is a WinRT/COM API. Reaching it from Java would mean hand-rolling the WinRT activation and
 * {@code IAsyncOperation} plumbing through JNA, which is a large amount of fragile interop for one HUD
 * widget. Windows PowerShell 5.1 can project WinRT directly, so a small script
 * ({@code assets/sakura/smtc-helper.ps1}) does the talking and prints one JSON line per poll; this class
 * starts it hidden, reads those lines from a background thread and exposes them as a
 * {@link MusicProvider}. Transport commands travel back through a command file, which keeps the pipe
 * one-directional and simple.</p>
 *
 * <p>The bridge never blocks the game: {@link #create()} waits for the first line on the caller's thread,
 * and the widget calls it from a worker thread of its own.</p>
 */
public final class SmtcMusicProvider implements MusicProvider {

	private static final Logger LOGGER = LoggerFactory.getLogger("sakura/music");

	private static final String SCRIPT_NAME = "smtc-helper.ps1";
	private static final String SCRIPT_RESOURCE = "/assets/sakura/" + SCRIPT_NAME;
	private static final long STARTUP_TIMEOUT_MILLIS = 6000L;
	private static final String ARTWORK_FILE = "album-art.img";
	private static final String COMMAND_FILE = "media-command.txt";

	private final Path directory;
	private final Path scriptPath;
	private final Path artworkPath;
	private final Path commandPath;

	private final CountDownLatch firstLine = new CountDownLatch(1);

	private Process process;
	private Thread readerThread;

	private volatile boolean running;
	private volatile boolean available;

	private volatile String status = "";
	private volatile String app;
	private volatile String title;
	private volatile String artist;
	private volatile String album;
	private volatile long positionMillis;
	private volatile long durationMillis;
	private volatile int artworkGeneration;

	private SmtcMusicProvider(Path directory) {
		this.directory = directory;
		this.scriptPath = directory.resolve(SCRIPT_NAME);
		this.artworkPath = directory.resolve(ARTWORK_FILE);
		this.commandPath = directory.resolve(COMMAND_FILE);
	}

	/**
	 * Starts the bridge and returns the provider, or a {@link MockMusicProvider} when the platform cannot
	 * support it (not Windows, no PowerShell, or the script failed to come up).
	 */
	public static MusicProvider create() {
		if (!isWindows()) {
			LOGGER.info("Now-playing integration needs Windows; the demo provider will be used");
			return new MockMusicProvider();
		}

		Path directory = ConfigPaths.getMediaDir();

		// Anything the pre-isolation layout wrote still sits in config/sakura/; adopt it before deciding the
		// bridge is starting fresh, so cover art and the command pipe survive the move.
		ConfigPaths.migrateLegacyMediaFile(SCRIPT_NAME);
		ConfigPaths.migrateLegacyMediaFile(ARTWORK_FILE);
		ConfigPaths.migrateLegacyMediaFile(COMMAND_FILE);

		try {
			SmtcMusicProvider provider = new SmtcMusicProvider(directory);

			if (provider.start()) {
				return provider;
			}

			provider.close();
		} catch (IOException | InterruptedException | RuntimeException exception) {
			if (exception instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}

			LOGGER.warn("Could not start the Windows media bridge: {}", exception.getMessage());
		}

		return new MockMusicProvider();
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	private boolean start() throws IOException, InterruptedException {
		Path powershell = findPowerShell();

		if (powershell == null) {
			LOGGER.warn("Windows PowerShell 5.1 was not found; the media bridge needs its WinRT support");
			return false;
		}

		Files.createDirectories(this.directory);
		extractScript();
		Files.writeString(this.commandPath, "");

		ProcessBuilder builder = new ProcessBuilder(powershell.toString(),
				"-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
				"-File", this.scriptPath.toString(),
				"-ArtworkPath", this.artworkPath.toString(),
				"-CommandPath", this.commandPath.toString(),
				"-IntervalMillis", "500");
		builder.directory(this.directory.toFile());
		builder.redirectErrorStream(true);

		this.process = builder.start();
		this.running = true;
		this.readerThread = new Thread(this::readLoop, "sakura-media-bridge");
		this.readerThread.setDaemon(true);
		this.readerThread.start();

		if (!this.firstLine.await(STARTUP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
			LOGGER.warn("The Windows media bridge reported nothing within {} ms", STARTUP_TIMEOUT_MILLIS);
			return false;
		}

		this.available = true;
		LOGGER.info("Windows media bridge running, script at {}", this.scriptPath);
		return true;
	}

	/** Writes the helper out of the jar next to the config, so it can be inspected and patched by hand. */
	private void extractScript() throws IOException {
		try (InputStream stream = SmtcMusicProvider.class.getResourceAsStream(SCRIPT_RESOURCE)) {
			if (stream == null) {
				throw new IOException("Missing resource " + SCRIPT_RESOURCE);
			}

			byte[] bytes = stream.readAllBytes();

			if (Files.exists(this.scriptPath) && Files.size(this.scriptPath) == bytes.length) {
				return;
			}

			Path temporary = this.scriptPath.resolveSibling(SCRIPT_NAME + ".tmp");
			Files.write(temporary, bytes);
			Files.move(temporary, this.scriptPath, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * @return {@code powershell.exe} (5.1), which is the only host that projects WinRT; {@code null} when
	 * it cannot be found
	 */
	private static Path findPowerShell() {
		String systemRoot = System.getenv("SystemRoot");

		if (systemRoot != null && !systemRoot.isBlank()) {
			Path path = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");

			if (Files.isRegularFile(path)) {
				return path;
			}
		}

		String pathVariable = System.getenv("PATH");

		if (pathVariable != null) {
			for (String entry : pathVariable.split(";")) {
				if (entry.isBlank()) {
					continue;
				}

				try {
					Path candidate = Path.of(entry.trim(), "powershell.exe");

					if (Files.isRegularFile(candidate)) {
						return candidate;
					}
				} catch (RuntimeException ignored) {
					// A malformed PATH entry is not our problem.
				}
			}
		}

		return null;
	}

	private void readLoop() {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(this.process.getInputStream(), StandardCharsets.UTF_8))) {
			String line;

			while (this.running && (line = reader.readLine()) != null) {
				String trimmed = line.trim();

				if (trimmed.isEmpty()) {
					continue;
				}

				if (trimmed.charAt(0) != '{') {
					// stderr is merged into the stream: PowerShell warnings are logged, never parsed.
					LOGGER.debug("[media bridge] {}", trimmed);
					continue;
				}

				if (accept(trimmed)) {
					this.firstLine.countDown();
				}
			}
		} catch (IOException exception) {
			if (this.running) {
				LOGGER.warn("The Windows media bridge stopped: {}", exception.getMessage());
			}
		} finally {
			this.available = false;
			this.firstLine.countDown();
		}
	}

	/**
	 * Applies one JSON line from the bridge.
	 *
	 * @return true when the line was a valid report
	 */
	private boolean accept(String line) {
		try {
			JsonObject root = JsonParser.parseString(line).getAsJsonObject();

			if (root.has("ok") && !root.get("ok").getAsBoolean()) {
				LOGGER.debug("[media bridge] {}", text(root, "error", "unknown error"));
				return false;
			}

			this.status = text(root, "status", "");
			this.app = text(root, "app", null);
			this.title = text(root, "title", null);
			this.artist = text(root, "artist", null);
			this.album = text(root, "album", null);
			this.positionMillis = Math.round(number(root, "pos", 0.0) * 1000.0);
			this.durationMillis = Math.round(number(root, "dur", 0.0) * 1000.0);

			if (root.has("art") && !root.get("art").isJsonNull()) {
				this.artworkGeneration = root.get("art").getAsInt();
			}

			return true;
		} catch (RuntimeException exception) {
			LOGGER.debug("Ignoring unreadable media bridge output: {}", line);
			return false;
		}
	}

	private static String text(JsonObject root, String key, String fallback) {
		if (!root.has(key) || root.get(key).isJsonNull()) {
			return fallback;
		}

		String value = root.get(key).getAsString();
		return value.isEmpty() ? fallback : value;
	}

	private static double number(JsonObject root, String key, double fallback) {
		if (!root.has(key) || root.get(key).isJsonNull()) {
			return fallback;
		}

		try {
			return root.get(key).getAsDouble();
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	// ------------------------------------------------------------------ MusicProvider

	@Override
	public String getSourceName() {
		return "Windows";
	}

	@Override
	public boolean isAvailable() {
		return this.available;
	}

	@Override
	public String getAppName() {
		return friendlyApp(this.app);
	}

	@Override
	public boolean isPlaying() {
		return "playing".equalsIgnoreCase(this.status);
	}

	@Override
	public String getTitle() {
		return this.title;
	}

	@Override
	public String getArtist() {
		return this.artist;
	}

	@Override
	public String getAlbum() {
		return this.album;
	}

	@Override
	public long getPositionMillis() {
		return this.positionMillis;
	}

	@Override
	public long getDurationMillis() {
		return this.durationMillis;
	}

	@Override
	public Path getArtworkPath() {
		return this.artworkGeneration > 0 ? this.artworkPath : null;
	}

	@Override
	public int getArtworkGeneration() {
		return this.artworkGeneration;
	}

	@Override
	public boolean canControl() {
		return this.available && (this.title != null || this.status != null && !this.status.isEmpty());
	}

	@Override
	public void send(Command command) {
		String token = switch (command) {
			case PLAY_PAUSE -> "playpause";
			case NEXT -> "next";
			case PREVIOUS -> "previous";
		};

		try {
			Files.createDirectories(this.directory);
			Files.writeString(this.commandPath, token);
		} catch (IOException exception) {
			LOGGER.warn("Could not send '{}' to the media bridge: {}", token, exception.getMessage());
		}
	}

	@Override
	public void close() {
		this.running = false;
		this.available = false;

		if (this.process != null && this.process.isAlive()) {
			try {
				Files.writeString(this.commandPath, "quit");
			} catch (IOException ignored) {
				// The process is going away either way.
			}

			this.process.destroy();

			try {
				if (!this.process.waitFor(1L, TimeUnit.SECONDS)) {
					this.process.destroyForcibly();
				}
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				this.process.destroyForcibly();
			}
		}

		this.process = null;
		this.readerThread = null;
	}

	/**
	 * Turns an AUMID such as {@code Spotify.exe} or {@code Microsoft.ZuneMusic_8wekyb3d8bbwe!Microsoft.ZuneMusic}
	 * into something worth showing on a HUD.
	 */
	private static String friendlyApp(String aumid) {
		if (aumid == null || aumid.isBlank()) {
			return null;
		}

		String lower = aumid.toLowerCase(Locale.ROOT);

		if (lower.contains("spotify")) {
			return "Spotify";
		}

		if (lower.contains("cloudmusic") || lower.contains("netease")) {
			return "NetEase Cloud Music";
		}

		if (lower.contains("qqmusic")) {
			return "QQ Music";
		}

		if (lower.contains("foobar")) {
			return "foobar2000";
		}

		if (lower.contains("aimp")) {
			return "AIMP";
		}

		if (lower.contains("musicbee")) {
			return "MusicBee";
		}

		if (lower.contains("potplayer")) {
			return "PotPlayer";
		}

		if (lower.contains("vlc")) {
			return "VLC";
		}

		if (lower.contains("zune") || lower.contains("media player")) {
			return "Media Player";
		}

		if (lower.contains("msedge")) {
			return "Edge";
		}

		if (lower.contains("chrome")) {
			return "Chrome";
		}

		if (lower.contains("firefox")) {
			return "Firefox";
		}

		int bang = aumid.indexOf('!');
		String trimmed = bang > 0 ? aumid.substring(0, bang) : aumid;
		int dot = trimmed.lastIndexOf('.');
		return dot >= 0 && dot < trimmed.length() - 1 ? trimmed.substring(dot + 1) : trimmed;
	}
}
