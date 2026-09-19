package com.naruka.client.render;

import net.minecraft.client.gui.DrawContext;

/**
 * The mapping from the menu window's local coordinates to screen coordinates, published once per frame by
 * {@code ClickGuiScreen}.
 *
 * <p>Widgets cache their hit boxes in local space, which is correct for input because the screen converts
 * clicks the same way. Clipping is different: {@link DrawContext#enableScissor} takes absolute screen
 * coordinates, so a widget that passes its local bounds directly clips the wrong part of the screen. That
 * mapping has to come from whoever applied the window transform.</p>
 */
public final class LocalTransform {

	private static float originX;
	private static float originY;
	private static float scale = 1.0f;

	private LocalTransform() {
	}

	/** Publishes the transform currently applied to the matrix stack. */
	public static void set(float originX, float originY, float scale) {
		LocalTransform.originX = originX;
		LocalTransform.originY = originY;
		LocalTransform.scale = scale <= 0.0f ? 1.0f : scale;
	}

	public static float getScale() {
		return scale;
	}

	/** Resets to the identity transform, e.g. for the HUD which draws in screen space already. */
	public static void reset() {
		set(0.0f, 0.0f, 1.0f);
	}

	public static int screenX(float localX) {
		return Math.round(originX + localX * scale);
	}

	public static int screenY(float localY) {
		return Math.round(originY + localY * scale);
	}

	/** Clips to a local-space rectangle, translated and scaled into screen space. */
	public static void enableScissor(DrawContext context, float localX, float localY, float width, float height) {
		int left = screenX(localX);
		int top = screenY(localY);

		context.enableScissor(left, top, screenX(localX + width), screenY(localY + height));
	}
}
