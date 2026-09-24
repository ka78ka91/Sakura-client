package com.sakura.client.hud.element;


import com.sakura.client.render.Theme;
import com.sakura.client.config.ConfigManager;
import com.sakura.client.hud.HudAnchor;
import com.sakura.client.hud.HudModule;
import com.sakura.client.render.Animations;
import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.RenderUtils.Align;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.Locale;

/**
 * Player position read-out, e.g. {@code XYZ  128.4 / 71.0 / -302.9}.
 *
 * <p>Drawn as a frosted glass pill. The three values are eased toward the real position with
 * {@link Animations#approach}, so the read-out glides instead of twitching with every sub-pixel move the
 * client makes, and it snaps on the first frame after joining a world so the animation never has to travel
 * in from nowhere.</p>
 */
public class CoordinatesElement extends HudModule {

	private static final float PANEL_WIDTH = 132.0f;
	private static final float PANEL_HEIGHT = 16.0f;
	private static final float PADDING_X = 6.0f;
	private static final float MARGIN = 4.0f;
	private static final float ACCENT_PILL_WIDTH = 2.0f;
	private static final float ACCENT_PILL_INSET = 4.0f;
	/** How quickly the read-out catches up with the player, in e-folds per second. */
	private static final float VALUE_SPEED = 12.0f;
	private static final int ACCENT_PILL_ALPHA = 0xD9;

	/** Glass body: a dark, slightly cool gradient drawn over the blurred world. */
	private static final float GLASS_SHADOW_SPREAD = 4.0f;

	private final Animations.Clock clock = new Animations.Clock();
	private float shownX;
	private float shownY;
	private float shownZ;
	private boolean primed;

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
			// Leaving the world drops the smoothing as well, so the next join starts from the real position.
			this.primed = false;
			return;
		}

		float delta = this.clock.tick();

		if (this.primed) {
			this.shownX = Animations.approach(this.shownX, (float) player.getX(), VALUE_SPEED, delta);
			this.shownY = Animations.approach(this.shownY, (float) player.getY(), VALUE_SPEED, delta);
			this.shownZ = Animations.approach(this.shownZ, (float) player.getZ(), VALUE_SPEED, delta);
		} else {
			this.primed = true;
			this.shownX = (float) player.getX();
			this.shownY = (float) player.getY();
			this.shownZ = (float) player.getZ();
		}

		float radius = cornerRadius();

		drawGlass(context, x, y, PANEL_WIDTH, PANEL_HEIGHT, radius);

		// The accent rail marks the leading edge of the pill, the way the client's windows are marked.
		RenderUtils.drawRoundedRect(context, x + ACCENT_PILL_INSET, y + ACCENT_PILL_INSET,
				ACCENT_PILL_WIDTH, PANEL_HEIGHT - ACCENT_PILL_INSET * 2.0f, ACCENT_PILL_WIDTH * 0.5f,
				RenderUtils.withAlpha(ConfigManager.get().accentColor, ACCENT_PILL_ALPHA));

		String text = String.format(Locale.ROOT, "%.1f / %.1f / %.1f", this.shownX, this.shownY, this.shownZ);
		// An extreme coordinate can be wider than the pill; trimming keeps the read-out inside its own box.
		float room = PANEL_WIDTH - PADDING_X - ACCENT_PILL_INSET - ACCENT_PILL_WIDTH - 3.0f;

		RenderUtils.drawTextVCentered(context, RenderUtils.trimToWidth(text, room), x + PANEL_WIDTH - PADDING_X,
				y, PANEL_HEIGHT, themed(Theme.text()), true, Align.RIGHT);
	}

	/** The shared Sakura glass material: gradient body, hairline border, drop shadow and an accent wash. */
	private static void drawGlass(DrawContext context, float x, float y, float width, float height, float radius) {
		RenderUtils.drawGlassPanel(context, x, y, width, height, radius,
				Theme.glassTop(), Theme.glassBottom(), Theme.glassBorder(), Theme.glassShadow(), GLASS_SHADOW_SPREAD);
		RenderUtils.drawAccentWash(context, x, y, width, height, radius, ConfigManager.get().accentColor, 1.0f);
	}
}
