package com.sakura.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Low-level 2D drawing helpers for the Sakura glass UI.
 *
 * <p>Every method here is built exclusively on top of the Minecraft 1.21.11 (Yarn) GUI API:
 * {@link DrawContext#fill(int, int, int, int, int)} and
 * {@link DrawContext#drawText(TextRenderer, String, int, int, int, boolean)}.
 * No legacy fixed-function GL calls ({@code GL11}, {@code Gui.drawRect}, {@code Tessellator}
 * immediate mode) are used anywhere.</p>
 *
 * <p>All colours are packed ARGB integers ({@code 0xAARRGGBB}), matching vanilla's convention.</p>
 */
public final class RenderUtils {

	/** Horizontal anchoring for {@link #drawText}. */
	public enum Align {
		LEFT,
		CENTER,
		RIGHT
	}

	private RenderUtils() {
	}

	// ------------------------------------------------------------------ font

	/** The client's main {@link TextRenderer}. */
	public static TextRenderer font() {
		return MinecraftClient.getInstance().textRenderer;
	}

	/** Rendered width of {@code text} in GUI pixels. */
	public static int textWidth(String text) {
		return font().getWidth(text);
	}

	/** Height of a single line of text in GUI pixels. */
	public static int fontHeight() {
		return font().fontHeight;
	}

	// ------------------------------------------------------------------ text

	/**
	 * Draws {@code text} left-aligned at ({@code x}, {@code y}).
	 *
	 * @param argb   packed ARGB colour, alpha is honoured
	 * @param shadow whether to draw the 1px drop shadow
	 */
	public static void drawText(DrawContext ctx, String text, float x, float y, int argb, boolean shadow) {
		if (text == null || text.isEmpty()) {
			return;
		}
		ctx.drawText(font(), text, Math.round(x), Math.round(y), argb, shadow);
	}

	/** Draws {@code text} at ({@code x}, {@code y}), anchored horizontally by {@code align}. */
	public static void drawText(DrawContext ctx, String text, float x, float y, int argb, boolean shadow, Align align) {
		if (text == null || text.isEmpty()) {
			return;
		}
		drawText(ctx, text, anchorX(text, x, align), y, argb, shadow);
	}

	/**
	 * Draws {@code text} horizontally anchored by {@code align} and vertically centred inside
	 * the box {@code [y, y + height]}.
	 */
	public static void drawTextVCentered(DrawContext ctx, String text, float x, float y, float height,
										 int argb, boolean shadow, Align align) {
		if (text == null || text.isEmpty()) {
			return;
		}
		float textY = y + (height - fontHeight()) / 2.0f + 1.0f;
		drawText(ctx, text, anchorX(text, x, align), textY, argb, shadow);
	}

	private static float anchorX(String text, float x, Align align) {
		return switch (align) {
			case LEFT -> x;
			case CENTER -> x - textWidth(text) / 2.0f;
			case RIGHT -> (float) (x - textWidth(text));
		};
	}

	// ------------------------------------------------------------ glass panels

	/**
	 * Draws a frosted-glass panel over the already-blurred backdrop.
	 *
	 * <p>The blur itself is not drawn here: 1.21.11 applies it as a whole-screen post effect from
	 * {@code Screen.renderBackground} (which {@code Screen.renderWithTooltip} calls before a screen's
	 * own {@code render}), and it may only be applied <em>once per frame</em> 鈥?calling
	 * {@code DrawContext.applyBlur()} from a screen's render method throws. There is also no way to blur
	 * a single rectangle through the public GUI API. This method therefore supplies the glass itself:
	 * the tint, the brighter sheen across the upper part and the inner top edge highlight that make a
	 * flat translucent rectangle read as a pane of glass over the blurred world.</p>
	 *
	 * @param tint base glass colour, typically {@code 0xE0141414}
	 */
	public static void drawBlurredRect(DrawContext ctx, float x, float y, float width, float height,
									   float radius, int tint) {
		if (width <= 0.0f || height <= 0.0f) {
			return;
		}

		drawRoundedRect(ctx, x, y, width, height, radius, tint);

		float sheenHeight = Math.min(height * 0.45f, 22.0f);

		if (sheenHeight > 1.0f) {
			drawRoundedRect(ctx, x, y, width, sheenHeight, radius, 0x0DFFFFFF);
		}

		if (width > 2.0f) {
			drawRoundedRect(ctx, x + 1.0f, y + 1.0f, width - 2.0f, 1.0f, 0.5f, 0x14FFFFFF);
		}
	}

	// ------------------------------------------------------------ rectangles

	/** Fills an axis-aligned rectangle. */
	public static void drawRect(DrawContext ctx, float x, float y, float width, float height, int argb) {
		if (width <= 0.0f || height <= 0.0f || (argb >>> 24) == 0) {
			return;
		}
		int x1 = Math.round(x);
		int y1 = Math.round(y);
		int x2 = Math.round(x + width);
		int y2 = Math.round(y + height);
		if (x2 <= x1 || y2 <= y1) {
			return;
		}
		ctx.fill(x1, y1, x2, y2, argb);
	}

	/**
	 * Fills a rectangle with uniformly rounded corners.
	 *
	 * <p>The shape is decomposed into three axis-aligned bands plus four circular corner fans.
	 * Each corner row is a single 1px-tall span whose length comes from the exact circle
	 * equation, so the arcs stay smooth and the whole call costs {@code O(radius)} fills
	 * instead of one fill per pixel column.</p>
	 *
	 * @param radius corner radius in GUI pixels, automatically clamped to half the shorter side
	 */
	public static void drawRoundedRect(DrawContext ctx, float x, float y, float width, float height,
									   float radius, int argb) {
		if (width <= 0.0f || height <= 0.0f || (argb >>> 24) == 0) {
			return;
		}

		float r = Math.min(radius, Math.min(width, height) * 0.5f);
		if (r < 1.0f) {
			drawRect(ctx, x, y, width, height, argb);
			return;
		}

		float right = x + width;
		float bottom = y + height;

		// Body: the middle band spans the full width, the top/bottom bands sit between the corners.
		drawRect(ctx, x, y + r, width, height - 2.0f * r, argb);
		drawRect(ctx, x + r, y, width - 2.0f * r, r, argb);
		drawRect(ctx, x + r, bottom - r, width - 2.0f * r, r, argb);

		int rows = Math.max(1, Math.round(r));
		for (int i = 0; i < rows; i++) {
			// Distance from the circle centre to the centre of this 1px row.
			double dy = r - (i + 0.5);
			double halfChord = Math.sqrt(Math.max(0.0, (double) r * r - dy * dy));
			float inset = (float) (r - halfChord);
			float band = r - inset;
			if (band <= 0.0f) {
				continue;
			}

			float topRow = y + i;
			float bottomRow = bottom - 1.0f - i;

			drawRect(ctx, x + inset, topRow, band, 1.0f, argb);
			drawRect(ctx, right - r, topRow, band, 1.0f, argb);
			drawRect(ctx, x + inset, bottomRow, band, 1.0f, argb);
			drawRect(ctx, right - r, bottomRow, band, 1.0f, argb);
		}
	}

	/**
	 * Draws a uniformly rounded outline.
	 *
	 * <p>The straight edges are four rectangles; each corner contributes a scanline ring whose
	 * inner and outer half-chords are derived from the outer radius {@code r} and the inner
	 * radius {@code r - thickness}, which keeps the stroke width visually constant through the arc.</p>
	 *
	 * @param thickness stroke width in GUI pixels, clamped to half the shorter side
	 */
	public static void drawBorder(DrawContext ctx, float x, float y, float width, float height,
								  float radius, float thickness, int argb) {
		if (width <= 0.0f || height <= 0.0f || thickness <= 0.0f || (argb >>> 24) == 0) {
			return;
		}

		float t = Math.min(thickness, Math.min(width, height) * 0.5f);
		float r = Math.min(radius, Math.min(width, height) * 0.5f);
		float right = x + width;
		float bottom = y + height;

		if (r < 1.0f) {
			drawRect(ctx, x, y, width, t, argb);
			drawRect(ctx, x, bottom - t, width, t, argb);
			drawRect(ctx, x, y + t, t, height - 2.0f * t, argb);
			drawRect(ctx, right - t, y + t, t, height - 2.0f * t, argb);
			return;
		}

		// Straight edges, shortened by the corner radius on both ends.
		drawRect(ctx, x + r, y, width - 2.0f * r, t, argb);
		drawRect(ctx, x + r, bottom - t, width - 2.0f * r, t, argb);
		drawRect(ctx, x, y + r, t, height - 2.0f * r, argb);
		drawRect(ctx, right - t, y + r, t, height - 2.0f * r, argb);

		double innerRadius = r - t;
		int rows = Math.max(1, Math.round(r));
		for (int i = 0; i < rows; i++) {
			double dy = r - (i + 0.5);
			double outerHalf = Math.sqrt(Math.max(0.0, (double) r * r - dy * dy));
			double innerHalf = (innerRadius > 0.0 && Math.abs(dy) < innerRadius)
					? Math.sqrt(innerRadius * innerRadius - dy * dy)
					: 0.0;

			float outerInset = (float) (r - outerHalf);
			float innerInset = (float) (r - innerHalf);
			float band = innerInset - outerInset;
			if (band <= 0.0f) {
				continue;
			}

			float topRow = y + i;
			float bottomRow = bottom - 1.0f - i;

			drawRect(ctx, x + outerInset, topRow, band, 1.0f, argb);
			drawRect(ctx, right - innerInset, topRow, band, 1.0f, argb);
			drawRect(ctx, x + outerInset, bottomRow, band, 1.0f, argb);
			drawRect(ctx, right - innerInset, bottomRow, band, 1.0f, argb);
		}
	}
}
