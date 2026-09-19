package com.naruka.client.hud;

/**
 * Placeholder {@link MusicProvider} that simulates the prototype's track
 * ("SYURI WITH LIQUIDGLASS...", 0:54 / 3:18) and keeps advancing so the progress bar animates.
 *
 * <p>Wall-clock based rather than tick based, so the bar moves smoothly even when the client is
 * running at a low tick rate.</p>
 */
public class MockMusicProvider implements MusicProvider {

	private static final String TITLE = "SYURI WITH LIQUIDGLASS...";
	private static final int DURATION_SECONDS = 198;
	private static final int START_SECONDS = 54;

	private final long epochMillis = System.currentTimeMillis();

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
		return null;
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
}
