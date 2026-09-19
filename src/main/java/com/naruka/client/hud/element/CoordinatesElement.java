package com.naruka.client.hud.element;

import com.naruka.client.hud.HudAnchor;
import com.naruka.client.hud.HudModule;
import com.naruka.client.render.RenderUtils;
import com.naruka.client.render.RenderUtils.Align;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;

/** Player position read-out, e.g. {@code XYZ  128.4 / 71.0 / -302.9}. */
public class CoordinatesElement extends HudModule {

	private static final float PANEL_WIDTH = 132.0f;
	private static final float PANEL_HEIGHT = 16.0f;
	private static final float PADDING_X = 6.0f;
	private static final float MARGIN = 4.0f;

	private static final int PANEL_BG = 0x66000000;
	private static final int TEXT = 0xFFFFFFFF;

	public CoordinatesElement() {
		super("Coordinates", "Player position read-out",
				HudAnchor.TOP_LEFT, MARGIN, MARGIN, false);
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
		ClientPlayerEntity player = MinecraftClient.getInstance().player;

		if (player == null) {
			return;
		}

		RenderUtils.drawRoundedRect(context, x, y, PANEL_WIDTH, PANEL_HEIGHT, cornerRadius(), PANEL_BG);

		String text = String.format("%.1f / %.1f / %.1f", player.getX(), player.getY(), player.getZ());
		RenderUtils.drawTextVCentered(context, text, x + PANEL_WIDTH - PADDING_X, y, PANEL_HEIGHT,
				TEXT, true, Align.RIGHT);
	}
}
