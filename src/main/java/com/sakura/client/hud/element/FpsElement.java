package com.sakura.client.hud.element;

import com.sakura.client.hud.HudAnchor;
import com.sakura.client.hud.HudModule;
import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.RenderUtils.Align;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Frames-per-second read-out. */
public class FpsElement extends HudModule {

	private static final float PANEL_WIDTH = 62.0f;
	private static final float PANEL_HEIGHT = 16.0f;
	private static final float PADDING_X = 6.0f;
	private static final float MARGIN = 4.0f;

	private static final int PANEL_BG = 0x66000000;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFAAAAAA;

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

		RenderUtils.drawRoundedRect(context, x, y, PANEL_WIDTH, PANEL_HEIGHT, cornerRadius(), PANEL_BG);
		RenderUtils.drawTextVCentered(context, String.valueOf(fps), x + PANEL_WIDTH - PADDING_X, y, PANEL_HEIGHT,
				TEXT, true, Align.RIGHT);
		RenderUtils.drawTextVCentered(context, "FPS", x + PADDING_X, y, PANEL_HEIGHT,
				TEXT_DIM, false, Align.LEFT);
	}
}
