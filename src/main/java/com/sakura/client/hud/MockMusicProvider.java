package com.sakura.client.hud;

/**
 * Demo {@link MusicProvider}: a fake track that keeps advancing so the widget can be positioned and
 * previewed when no real media session exists.
 *
 * <p>It is the fallback, not the feature: {@link SmtcMusicProvider} is what talks to the machine. The
 * title is the one the design prototype showed, which makes it obvious at a glance that this is the
 * placeholder and not the user's actual music.</p>
 *
 * <p>Wall-clock based rather than tick based, so the bar moves smoothly even when the client is running
 * at a low tick rate.</p>
 */
public class MockMusicProvider implements MusicProvider {

	private static final String TITLE = "SYURI WITH LIQUIDGLASS...";
	private static final String ARTIST = "Demo track";
	private static final int DURATION_SECONDS = 198;
	private static final int START_SECONDS = 54;

	private final long epochMillis = System.currentTimeMillis();

	@Override
	public String getSourceName() {
		return "Demo";
	}

	@Override
	public boolean isPlaying() {
		return true;
	}

	@Override
	public String getTitle() {
		return TITLE;
	}

	@Override
	public String getArtist() {
		return ARTIST;
	}

	@Override
	public int getPositionSeconds() {
		long elapsed = (System.currentTimeMillis() - this.epochMillis) / 1000L;
		return (int) ((START_SECONDS + elapsed) % DURATION_SECONDS);
	}

	@Override
	public int getDurationSeconds() {
		return DURATION_SECONDS;
	}

	@Override
	public long getPositionMillis() {
		return getPositionSeconds() * 1000L;
	}

	@Override
	public long getDurationMillis() {
		return DURATION_SECONDS * 1000L;
	}
}
