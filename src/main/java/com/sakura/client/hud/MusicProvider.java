package com.sakura.client.hud;

import java.nio.file.Path;

/**
 * Source of now-playing information for the music widget.
 *
 * <p>Everything the widget needs is expressed here, and every method has a harmless default, so a new
 * backend only has to override what it can actually answer. The client ships two implementations:</p>
 *
 * <ul>
 *     <li>{@link SmtcMusicProvider} — the real one, on Windows: it reads the OS media session, so it sees
 *     whatever the machine is playing (Spotify, NetEase Cloud Music, QQ Music, a browser tab, foobar2000).</li>
 *     <li>{@link MockMusicProvider} — a demo track used when nothing real is available, so the widget can
 *     still be positioned and previewed.</li>
 * </ul>
 */
public interface MusicProvider {

	/** A transport command the widget can send back to the player. */
	enum Command {
		PLAY_PAUSE,
		NEXT,
		PREVIOUS
	}

	/** Label of the backend, shown in logs, e.g. {@code Windows} or {@code Demo}. */
	default String getSourceName() {
		return "Unknown";
	}

	/** False when the backend cannot report anything at all, e.g. the bridge process died. */
	default boolean isAvailable() {
		return true;
	}

	/** Friendly name of the application that owns the session, or {@code null} when unknown. */
	default String getAppName() {
		return null;
	}

	default boolean isPlaying() {
		return false;
	}

	/** Track title, or {@code null} when nothing is playing. */
	default String getTitle() {
		return null;
	}

	/** Artist line, or {@code null} to omit it. */
	default String getArtist() {
		return null;
	}

	/** Album name, or {@code null} to omit it. */
	default String getAlbum() {
		return null;
	}

	default int getPositionSeconds() {
		return (int) (getPositionMillis() / 1000L);
	}

	default int getDurationSeconds() {
		return (int) (getDurationMillis() / 1000L);
	}

	/** Playback position in milliseconds; used for a bar that moves smoothly rather than in whole seconds. */
	default long getPositionMillis() {
		return 0L;
	}

	/** Track length in milliseconds, zero when unknown (e.g. a live stream). */
	default long getDurationMillis() {
		return 0L;
	}

	default float getProgress() {
		long duration = getDurationMillis();

		if (duration <= 0L) {
			return 0.0f;
		}

		return Math.max(0.0f, Math.min(1.0f, getPositionMillis() / (float) duration));
	}

	/** File holding the current cover art, or {@code null} when there is none. */
	default Path getArtworkPath() {
		return null;
	}

	/** Bumped every time new artwork is written, so the widget knows to decode it again. */
	default int getArtworkGeneration() {
		return 0;
	}

	/** True when {@link #send} will do something. */
	default boolean canControl() {
		return false;
	}

	/** Sends a transport command; the widget calls this when its controls are clicked. */
	default void send(Command command) {
	}

	/** Called once per client tick while the widget is enabled. */
	default void tick() {
	}

	/** Releases whatever the backend holds. Called when the widget is switched off. */
	default void close() {
	}
}
