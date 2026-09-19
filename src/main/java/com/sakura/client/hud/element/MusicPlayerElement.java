package com.sakura.client.hud.element;

import com.sakura.client.hud.HudAnchor;
import com.sakura.client.hud.HudModule;
import com.sakura.client.hud.MockMusicProvider;
import com.sakura.client.hud.MusicProvider;
import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.RenderUtils.Align;
import net.minecraft.client.gui.DrawContext;

/**
 * Now-playing widget: cover art placeholder, title, elapsed/total time and a progress bar.
 *
 * <p>Data comes from a {@link MusicProvider}; the shipped default is {@link MockMusicProvider}.</p>
 */
public class MusicPlayerElement extends HudModule {

	private static final float PANEL_WIDTH = 210.0f;
	private static final float PANEL_HEIGHT = 46.0f;
	private static final float ART_SIZE = 16.0f;
	private static final float ART_INSET = 10.0f;

	private static final int PANEL_BG = 0x66000000;
	private static final int PANEL_BORDER = 0x1AFFFFFF;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFAAAAAA;
	private static final int PROGRESS_TRACK = 0x33FFFFFF;
	private static final int PROGRESS_FILL = 0xFFFFFFFF;
	private static final int ALBUM_ART = 0xFF7A4BD0;

	private MusicProvider provider = new MockMusicProvider();

	public MusicPlayerElement() {
		super("Music Player", "Now playing widget with cover art and progress bar",
				HudAnchor.TOP_CENTER, 0.0f, 6.0f, true);
	}

	public void setProvider(MusicProvider provider) {
		this.provider = provider;
	}

	public MusicProvider getProvider() {
		return this.provider;
	}

	@Override
	public float getWidth() {
		return PANEL_WIDTH;
	}

	@Override
	public float getHeight() {
		return PANEL_HEIGHT;
	}

	@Override
	public void onTick() {
		this.provider.tick();
	}

	@Override
	public void render(DrawContext context, float x, float y) {
		float radius = cornerRadius();

		RenderUtils.drawRoundedRect(context, x, y, PANEL_WIDTH, PANEL_HEIGHT, radius, PANEL_BG);
		RenderUtils.drawBorder(context, x, y, PANEL_WIDTH, PANEL_HEIGHT, radius, 1.0f, PANEL_BORDER);

		float artX = x + ART_INSET;
		float artY = y + 8.0f;
		RenderUtils.drawRoundedRect(context, artX, artY, ART_SIZE, ART_SIZE, 4.0f, ALBUM_ART);
		RenderUtils.drawTextVCentered(context, "\u266A", artX + ART_SIZE / 2.0f, artY, ART_SIZE,
				TEXT, false, Align.CENTER);

		float textX = artX + ART_SIZE + 8.0f;
		RenderUtils.drawText(context, this.provider.getTitle(), textX, artY - 1.0f, TEXT, true);
		RenderUtils.drawText(context, formatTime(this.provider.getPositionSeconds()) + " / "
						+ formatTime(this.provider.getDurationSeconds()),
				textX, artY + 10.0f, TEXT_DIM, false);

		float barX = x + ART_INSET;
		float barY = y + PANEL_HEIGHT - 9.0f;
		float barWidth = PANEL_WIDTH - ART_INSET * 2.0f;

		RenderUtils.drawRoundedRect(context, barX, barY, barWidth, 3.0f, 1.5f, PROGRESS_TRACK);
		RenderUtils.drawRoundedRect(context, barX, barY, barWidth * this.provider.getProgress(), 3.0f, 1.5f,
				PROGRESS_FILL);
	}

	private static String formatTime(int totalSeconds) {
		int safe = Math.max(0, totalSeconds);
		return (safe / 60) + ":" + String.format("%02d", safe % 60);
	}
}
