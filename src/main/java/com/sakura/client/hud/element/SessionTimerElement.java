package com.sakura.client.hud.element;

import com.sakura.client.config.ConfigManager;
import com.sakura.client.hud.HudAnchor;
import com.sakura.client.hud.HudModule;
import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.RenderUtils.Align;
import com.sakura.client.render.Theme;
import com.sakura.client.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;

/**
 * How long the client has been running this session.
 *
 * <p>Counted from the object's own construction, which happens once during client start-up, rather than from the
 * first frame or the first tick: the number should include the time spent on the title screen, since that is
 * part of how long the client has been open.</p>
 *
 * <p>The read-out switches to hours only once there are any, so a short session reads {@code 04:12} instead of
 * {@code 00:04:12}.</p>
 */
public class SessionTimerElement extends HudModule {

	private static final float PANEL_HEIGHT = 16.0f;
	private static final float PADDING_X = 6.0f;
	private static final float LABEL_GAP = 6.0f;
	private static final float MARGIN = 4.0f;
	private static final float GLASS_SHADOW_SPREAD = 4.0f;

	/** Start of the session, in milliseconds, taken when the element is constructed. */
	private final long startedAt = System.currentTimeMillis();

	private final BooleanSetting showLabel = setting(new BooleanSetting("Label",
			"Show the word \"Session\" in front of the time.", true));

	public SessionTimerElement() {
		super("Session", "How long the client has been running",
				HudAnchor.BOTTOM_RIGHT, MARGIN, MARGIN + PANEL_HEIGHT + 2.0f, false);
	}

	@Override
	public float getWidth() {
		return PADDING_X * 2.0f + labelWidth() + RenderUtils.textWidth(elapsed());
	}

	@Override
	public float getHeight() {
		return PANEL_HEIGHT;
	}

	@Override
	public void render(DrawContext context, float x, float y) {
		float radius = cornerRadius();
		RenderUtils.drawGlassPanel(context, x, y, getWidth(), PANEL_HEIGHT, radius,
				Theme.glassTop(), Theme.glassBottom(), Theme.glassBorder(), Theme.glassShadow(), GLASS_SHADOW_SPREAD);
		RenderUtils.drawAccentWash(context, x, y, getWidth(), PANEL_HEIGHT, radius,
				ConfigManager.get().accentColor, 1.0f);

		float textY = y + (PANEL_HEIGHT - RenderUtils.fontHeight()) / 2.0f + 1.0f;

		if (this.showLabel.get()) {
			RenderUtils.drawText(context, "Session", x + PADDING_X, textY, themed(Theme.textDim()), true);
		}

		RenderUtils.drawText(context, elapsed(), x + getWidth() - PADDING_X, textY,
				themed(Theme.text()), true, Align.RIGHT);
	}

	private float labelWidth() {
		return this.showLabel.get() ? RenderUtils.textWidth("Session") + LABEL_GAP : 0.0f;
	}

	/** @return the session length as {@code mm:ss}, or {@code h:mm:ss} once it passes an hour */
	private String elapsed() {
		long seconds = Math.max(0L, (System.currentTimeMillis() - this.startedAt) / 1000L);
		long hours = seconds / 3600L;
		long minutes = (seconds % 3600L) / 60L;
		long remainder = seconds % 60L;

		return hours > 0L
				? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainder)
				: String.format(Locale.ROOT, "%02d:%02d", minutes, remainder);
	}
}
