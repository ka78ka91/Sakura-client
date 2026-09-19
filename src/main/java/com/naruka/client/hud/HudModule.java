package com.naruka.client.hud;

import com.naruka.client.config.ConfigManager;
import com.naruka.client.module.Category;
import com.naruka.client.module.Module;
import net.minecraft.client.gui.DrawContext;

/**
 * A HUD element: a {@link Module} that can paint itself and be repositioned.
 *
 * <p>Size is queried every frame through {@link #getWidth()} / {@link #getHeight()} so an element may
 * grow with its content (the module list does exactly that). Position is either derived from the
 * element's {@link HudAnchor} default or taken from an explicit drag position.</p>
 */
public abstract class HudModule extends Module {

	private final HudAnchor anchor;
	private final float defaultOffsetX;
	private final float defaultOffsetY;

	private float explicitX;
	private float explicitY;
	private boolean positioned;

	protected HudModule(String name, String description, HudAnchor anchor, float offsetX, float offsetY,
						boolean enabledByDefault) {
		super(name, Category.HUD, description, enabledByDefault);
		this.anchor = anchor;
		this.defaultOffsetX = offsetX;
		this.defaultOffsetY = offsetY;
	}

	public final HudAnchor getAnchor() {
		return this.anchor;
	}

	/** Corner radius shared by HUD panels, driven by the "HUD corner radius" setting. */
	protected final float cornerRadius() {
		return ConfigManager.get().hudCornerRadius;
	}

	public abstract float getWidth();

	public abstract float getHeight();

	/** Paints the element with its top-left corner at ({@code x}, {@code y}). */
	public abstract void render(DrawContext context, float x, float y);

	/** Screen-space top-left corner for this frame. */
	public final float resolveX(int screenWidth) {
		if (this.positioned) {
			return clamp(this.explicitX, 0.0f, Math.max(0.0f, screenWidth - getWidth()));
		}

		return this.anchor.resolveX(screenWidth, getWidth(), this.defaultOffsetX);
	}

	public final float resolveY(int screenHeight) {
		if (this.positioned) {
			return clamp(this.explicitY, 0.0f, Math.max(0.0f, screenHeight - getHeight()));
		}

		return this.anchor.resolveY(screenHeight, getHeight(), this.defaultOffsetY);
	}

	public final boolean isPositioned() {
		return this.positioned;
	}

	public final float getExplicitX() {
		return this.explicitX;
	}

	public final float getExplicitY() {
		return this.explicitY;
	}

	public final void setPosition(float x, float y) {
		this.explicitX = x;
		this.explicitY = y;
		this.positioned = true;
	}

	/** Snaps back to the element's default anchor. */
	public final void clearPosition() {
		this.positioned = false;
	}

	public final boolean isHovered(double mouseX, double mouseY, int screenWidth, int screenHeight) {
		float x = resolveX(screenWidth);
		float y = resolveY(screenHeight);

		return mouseX >= x && mouseX < x + getWidth() && mouseY >= y && mouseY < y + getHeight();
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}
}
