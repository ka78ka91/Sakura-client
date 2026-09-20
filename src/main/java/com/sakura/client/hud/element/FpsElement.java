package com.sakura.client.hud.element;

import com.sakura.client.config.ConfigManager;
import com.sakura.client.hud.HudAnchor;
import com.sakura.client.hud.HudModule;
import com.sakura.client.render.Animations;
import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.RenderUtils.Align;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Frames-per-second read-out.
 *
 * <p>The number on screen is eased toward the value the client reports, because the raw counter jumps by
 * several frames every time the render loop hiccups and a read-out that flickers between 240 and 190 is
 * unreadable. The colour follows the smoothed value as well, so it drifts from green to amber to red as the
 * frame rate falls rather than flipping between them.</p>
 */
public class FpsElement extends HudModule {

	private static final float PANEL_WIDTH = 62.0f;
	private static final float PANEL_HEIGHT = 16.0f;
	private static final float PADDING_X = 6.0f;
	private static final float MARGIN = 4.0f;
	/** How quickly the read-out catches up with the real frame rate, in e-folds per second. */
	private static final float VALUE_SPEED = 4.5f;
	/** Frame rates at or below this count as unplayable, at or above the second one as flawless. */
	private static final float BAD_FPS = 30.0f;
	private static final float GOOD_FPS = 120.0f;

	private static final int TEXT_DIM = 0xFFAAAAAA;
	private static final int FPS_LOW = 0xFFFF5A5A;
	private static final int FPS_MID = 0xFFFFC04D;
	private static final int FPS_HIGH = 0xFF6BE89A;

	/** Glass body: a dark, slightly cool gradient drawn over the blurred world. */
	private static final int GLASS_TOP = 0xB414141A;
	private static final int GLASS_BOTTOM = 0x8C0A0A0F;
	private static final int GLASS_BORDER = 0x2EFFFFFF;
	private static final int GLASS_SHADOW = 0x66000000;
	private static final float GLASS_SHADOW_SPREAD = 4.0f;

	private final Animations.Clock clock = new Animations.Clock();
	private float shownFps;
	private boolean primed;

	public FpsElement() {
		super("FPS", "Frames per second read-out",
				HudAnchor.TOP_LEFT, MARGIN, MARGIN + PANEL_HEIGHT + 2.0f, false);
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
	public void render(DrawContext context, float x, float y) {
		int fps = MinecraftClient.getInstance().getCurrentFps();
		float delta = this.clock.tick();

		if (this.primed) {
			this.shownFps = Animations.approach(this.shownFps, fps, VALUE_SPEED, delta);
		} else {
			this.primed = true;
			this.shownFps = fps;
		}

		float radius = cornerRadius();

		drawGlass(context, x, y, PANEL_WIDTH, PANEL_HEIGHT, radius);

		RenderUtils.drawTextVCentered(context, String.valueOf(Math.round(this.shownFps)),
				x + PANEL_WIDTH - PADDING_X, y, PANEL_HEIGHT, fpsColor(this.shownFps), true, Align.RIGHT);
		RenderUtils.drawTextVCentered(context, "FPS", x + PADDING_X, y, PANEL_HEIGHT,
				TEXT_DIM, true, Align.LEFT);
	}

	/** @return the colour of the read-out: red on a stuttering client, green on a smooth one */
	private static int fpsColor(float fps) {
		float quality = Animations.clamp01((fps - BAD_FPS) / (GOOD_FPS - BAD_FPS));

		return quality < 0.5f
				? RenderUtils.mix(FPS_LOW, FPS_MID, quality * 2.0f)
				: RenderUtils.mix(FPS_MID, FPS_HIGH, (quality - 0.5f) * 2.0f);
	}

	/** The shared Sakura glass material: gradient body, hairline border, drop shadow and an accent wash. */
	private static void drawGlass(DrawContext context, float x, float y, float width, float height, float radius) {
		RenderUtils.drawGlassPanel(context, x, y, width, height, radius,
				GLASS_TOP, GLASS_BOTTOM, GLASS_BORDER, GLASS_SHADOW, GLASS_SHADOW_SPREAD);
		RenderUtils.drawAccentWash(context, x, y, width, height, radius, ConfigManager.get().accentColor, 1.0f);
	}
}
