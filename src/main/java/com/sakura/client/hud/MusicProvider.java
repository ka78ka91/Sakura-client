package com.sakura.client.hud;

/**
 * Source of now-playing information for the music widget.
 *
 * <p>This is the seam for real media integration. The client ships with {@link MockMusicProvider},
 * because talking to an actual player (an OS media session, a local library, a streaming service)
 * needs an external dependency and usually credentials; implementing this interface is all that is
 * required to swap one in.</p>
 */
public interface MusicProvider {

	boolean isPlaying();

	/** Track title, may be truncated by the widget. */
	String getTitle();

	/** Artist line; return {@code null} to omit it. */
	default String getArtist() {
		return null;
	}

	int getPositionSeconds();

	int getDurationSeconds();

	default float getProgress() {
		int duration = getDurationSeconds();

		if (duration <= 0) {
			return 0.0f;
		}

		return Math.max(0.0f, Math.min(1.0f, getPositionSeconds() / (float) duration));
	}

	/** Called once per client tick while the widget is enabled. */
	default void tick() {
	}
}
