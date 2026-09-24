package com.sakura.client.render;

import net.minecraft.client.gui.DrawContext;

/**
 * The mapping from the menu window's local coordinates to screen coordinates, published once per frame by
 * {@code ClickGuiScreen}.
 *
 * <p>Widgets cache their hit boxes in local space, which is correct for input because the screen converts
 * clicks the same way, and it is what {@link #enableScissor} expects as well: in 1.21.11
 * {@link DrawContext#enableScissor(int, int, int, int)} transforms its arguments through the current matrix
 * itself ({@code ScreenRect.transform(matrices)}) and stores the result in screen space. A caller that has
 * already applied the window transform must therefore hand it <em>local</em> coordinates.</p>
 *
 * <p>Passing screen coordinates while the window transform is active applies it twice, which pushes the clip
 * box away from the window origin by {@code origin * scale} — that is what used to shave the first glyph off
 * every row drawn near the left edge of the menu. The same rule applies to any code that installs its own
 * transform (the notification stack, for instance): clip in the space you draw in.</p>
 *
 * <p>{@link #screenX} and {@link #screenY} stay available for the cases that genuinely need a point converted
 * to screen space, but they are not part of the clipping path.</p>
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

	/**
	 * Clips to a rectangle expressed in the local space the window transform draws in.
	 *
	 * <p>No conversion happens here: {@code DrawContext.enableScissor} applies the matrix that is currently on
	 * the stack, so translating these bounds into screen space first would transform them a second time and
	 * shift the clip box by {@code origin * scale}. Rounded to whole local pixels, which the transform then
	 * scales: the clip box stays on pixel boundaries at scale 1 and inside the intended edge at any scale.</p>
	 */
	public static void enableScissor(DrawContext context, float localX, float localY, float width, float height) {
		context.enableScissor(Math.round(localX), Math.round(localY),
				Math.round(localX + width), Math.round(localY + height));
	}
}
