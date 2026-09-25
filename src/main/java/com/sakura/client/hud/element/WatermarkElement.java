package com.sakura.client.hud.element;

import com.sakura.client.config.ConfigManager;
import com.sakura.client.hud.HudAnchor;
import com.sakura.client.hud.HudModule;
import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.RenderUtils.Align;
import com.sakura.client.render.Theme;
import com.sakura.client.setting.BooleanSetting;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * The client's name and version, as a small panel.
 *
 * <p>The version comes from the running game rather than from a constant, so a build can never claim to be for a
 * version it was not compiled against. {@code SharedConstants.getGameVersion().name()} is the same string the
 * title screen shows.</p>
 *
 * <p>Reading the frame rate is optional and off by default: the FPS element already exists, and two frame
 * counters on one HUD is one too many unless the player deliberately wants the watermark to carry it.</p>
 */
public class WatermarkElement extends HudModule {

	private static final float PANEL_HEIGHT = 16.0f;
	private static final float PADDING_X = 6.0f;
	private static final float LABEL_GAP = 6.0f;
	private static final float MARGIN = 4.0f;
	private static final float GLASS_SHADOW_SPREAD = 4.0f;
	private static final String NAME = "Sakura Client";

	private final BooleanSetting showVersion = setting(new BooleanSetting("Version",
			"Show the Minecraft version next to the name.", true));
	private final BooleanSetting showFps = setting(new BooleanSetting("FPS",
			"Also show the current frame rate.", false));

	public WatermarkElement() {
		super("Watermark", "Client name and version", HudAnchor.TOP_RIGHT, MARGIN, MARGIN, false);
	}

	@Override
	public float getWidth() {
		return PADDING_X * 2.0f + RenderUtils.textWidth(NAME) + labelWidth();
	}

	@Override
	public float getHeight() {
		return PANEL_HEIGHT;
	}

	@Override
	public void render(DrawContext context, float x, float y) {
		float radius = cornerRadius();
		drawPanel(context, x, y, getWidth(), PANEL_HEIGHT, radius);

		float textY = y + (PANEL_HEIGHT - RenderUtils.fontHeight()) / 2.0f + 1.0f;

		RenderUtils.drawText(context, NAME, x + PADDING_X, textY, themed(Theme.text()), true);

		String label = label();

		if (!label.isEmpty()) {
			RenderUtils.drawText(context, label, x + getWidth() - PADDING_X, textY,
					themed(Theme.textDim()), true, Align.RIGHT);
		}
	}

	/** @return the trailing read-out, e.g. {@code "1.21.11  240 fps"}, or an empty string when both are off */
	private String label() {
		StringBuilder text = new StringBuilder();

		if (this.showVersion.get()) {
			text.append(version());
		}

		if (this.showFps.get()) {
			if (text.length() > 0) {
				text.append("  ");
			}

			text.append(MinecraftClient.getInstance().getCurrentFps()).append(" fps");
		}

		return text.toString();
	}

	private float labelWidth() {
		String label = label();

		return label.isEmpty() ? 0.0f : LABEL_GAP + RenderUtils.textWidth(label);
	}

	/** @return the running game's version, or an empty string if the client has not created one yet */
	private static String version() {
		if (SharedConstants.getGameVersion() == null) {
			return "";
		}

		String name = SharedConstants.getGameVersion().name();

		return name == null ? "" : name;
	}

	/** The shared Sakura panel material, kept identical to the other HUD elements. */
	private static void drawPanel(DrawContext context, float x, float y, float width, float height, float radius) {
		RenderUtils.drawGlassPanel(context, x, y, width, height, radius,
				Theme.glassTop(), Theme.glassBottom(), Theme.glassBorder(), Theme.glassShadow(), GLASS_SHADOW_SPREAD);
		RenderUtils.drawAccentWash(context, x, y, width, height, radius, ConfigManager.get().accentColor, 1.0f);
	}
}
