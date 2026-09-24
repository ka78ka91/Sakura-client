package com.sakura.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

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

	/**
	 * Upper bound on the number of rounded rectangles a soft shadow is built from.
	 *
	 * <p>See {@link #drawSoftShadow}: the layers are the most expensive thing the HUD draws, and past this
	 * point they stop being visible.</p>
	 */
	private static final int MAX_SHADOW_LAYERS = 2;

	/**
	 * Panel width below which {@link #drawGlassPanel} skips its hairline.
	 *
	 * <p>A key cap is a couple of dozen pixels across and carries a glyph centred in it; the outline would run
	 * along the text and cost more than the fill it surrounds. Nothing that wide is ever a panel the player has
	 * to read an edge on.</p>
	 */
	private static final float MIN_BORDER_WIDTH = 28.0f;

	// ------------------------------------------------------------------ font

	/** The client's main {@link TextRenderer}, through {@link FontManager}. */
	public static TextRenderer font() {
		return FontManager.font();
	}

	/** Rendered width of {@code text} in GUI pixels. */
	public static int textWidth(String text) {
		return font().getWidth(text);
	}

	/** Height of a single line of text in GUI pixels. */
	public static int fontHeight() {
		return font().fontHeight;
	}

	/**
	 * Shortens {@code text} until it fits into {@code maxWidth} pixels, marking the cut with an ellipsis.
	 *
	 * <p>Uses the font's own measurement rather than counting characters, so wide glyphs are accounted for.</p>
	 */
	public static String trimToWidth(String text, float maxWidth) {
		if (text == null || text.isEmpty() || maxWidth <= 0.0f) {
			return "";
		}

		TextRenderer renderer = font();

		if (renderer.getWidth(text) <= maxWidth) {
			return text;
		}

		String ellipsis = "...";
		float room = maxWidth - renderer.getWidth(ellipsis);

		if (room <= 0.0f) {
			return "";
		}

		return renderer.trimToWidth(text, (int) room) + ellipsis;
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

	// ------------------------------------------------------------ rectangles

	/** Fills an axis-aligned rectangle. */
	public static void drawRect(DrawContext ctx, float x, float y, float width, float height, int argb) {		if (width <= 0.0f || height <= 0.0f || (argb >>> 24) == 0) {
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
	 * <p>Each row is split into up to two spans — the left corner's, and the right corner's — computed from the
	 * exact circle equation, and consecutive rows that land on the same pair of spans are merged into one fill.
	 * That is the cheapest decomposition that is still pixel-exact: the corners are the only part of the shape
	 * that differs from the bounding rectangle, so the spans always stop where the arc begins.</p>
	 *
	 * <p>It matters because this is the shape behind every surface the client draws, including each of the
	 * twelve HUD elements and each of the seven key caps in the keystrokes overlay. The obvious implementation
	 * — a full-width band plus four 1px corner rows — pays three fills per row for the two spans and the middle
	 * between them, which on the small radii this UI uses is almost entirely redundant. Here a row whose corners
	 * have not moved is one fill.</p>
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

		int rows = Math.max(1, Math.round(r));
		int top = Math.round(y);
		int bottom = Math.round(y + height);
		int left = Math.round(x);
		int right = Math.round(x + width);

		// The corner arcs only occupy the first and last `rows` rows; everything between them is the full
		// rectangle. With a small radius on a short panel the two arcs overlap and there is no middle at all.
		int arcTopEnd = Math.min(top + rows, bottom);
		int arcBottomStart = Math.max(bottom - rows, arcTopEnd);

		int[] inset = new int[rows];

		for (int row = 0; row < rows; row++) {
			double dy = r - (row + 0.5);
			double halfChord = Math.sqrt(Math.max(0.0, (double) r * r - dy * dy));
			inset[row] = (int) Math.round(r - halfChord);
		}

		fillRoundedRows(ctx, left, right, top, bottom, arcTopEnd - top, arcBottomStart, inset, argb);
	}

	/**
	 * Emits the merged spans for the rounded ends of a shape.
	 *
	 * @param arcTopEnd    first row that is no longer part of the top arc
	 * @param arcBottomStart first row that is part of the bottom arc
	 * @param inset        per-row inset on each side, index 0 being the outermost row of an arc
	 */
	private static void fillRoundedRows(DrawContext ctx, int left, int right, int top, int bottom,
										int arcTopEnd, int arcBottomStart, int[] inset, int argb) {
		int row = top;

		// Top arc, walking inward.
		while (row < arcTopEnd) {
			int index = row - top;
			int value = inset[index];
			int leftEdge = left + value;
			int rightEdge = right - value;
			int runEnd = row + 1;

			while (runEnd < arcTopEnd && inset[runEnd - top] == value) {
				runEnd++;
			}

			drawRect(ctx, leftEdge, row, rightEdge - leftEdge, runEnd - row, argb);
			row = runEnd;
		}

		// The middle, which has no corner at all.
		if (row < arcBottomStart) {
			drawRect(ctx, left, row, right - left, arcBottomStart - row, argb);
			row = arcBottomStart;
		}

		// Bottom arc, walking outward.
		while (row < bottom) {
			int index = Math.min(inset.length - 1, bottom - row - 1);
			int value = inset[index];
			int leftEdge = left + value;
			int rightEdge = right - value;
			int runEnd = row + 1;

			while (runEnd < bottom && inset[Math.min(inset.length - 1, bottom - runEnd - 1)] == value) {
				runEnd++;
			}

			drawRect(ctx, leftEdge, row, rightEdge - leftEdge, runEnd - row, argb);
			row = runEnd;
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

	// ------------------------------------------------------------ colour math

	/** True when the colour would draw nothing. */
	public static boolean isVisible(int argb) {
		return (argb >>> 24) != 0;
	}

	/** Replaces the alpha channel of {@code argb}. */
	public static int withAlpha(int argb, int alpha) {
		int clamped = alpha < 0 ? 0 : (alpha > 255 ? 255 : alpha);
		return (clamped << 24) | (argb & 0x00FFFFFF);
	}

	/** Scales the existing alpha, e.g. {@code multiplyAlpha(colour, 0.5f)} to fade a colour by half. */
	public static int multiplyAlpha(int argb, float factor) {
		int alpha = Math.round((argb >>> 24) * factor);
		return withAlpha(argb, alpha);
	}

	/**
	 * Interpolates two packed ARGB colours channel by channel, alpha included.
	 *
	 * @param delta 0 returns {@code from}, 1 returns {@code to}
	 */
	public static int mix(int from, int to, float delta) {
		float t = delta < 0.0f ? 0.0f : (delta > 1.0f ? 1.0f : delta);
		int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
		int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** Multiplies the RGB channels, keeping alpha. {@code factor} below 1 darkens, above 1 brightens. */
	public static int scaleRgb(int argb, float factor) {
		int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * factor));
		int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * factor));
		int b = Math.min(255, Math.round((argb & 0xFF) * factor));
		return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
	}

	/** Mixes the colour toward white, the classic "hover" highlight. */
	public static int brighten(int argb, float amount) {
		return mix(argb, 0xFFFFFFFF, amount);
	}

	/** Mixes the colour toward black, for pressed states and depth. */
	public static int darken(int argb, float amount) {
		return mix(argb, 0xFF000000, amount);
	}

	// -------------------------------------------------------- gradients & depth

	/**
	 * Fills a rectangle with a vertical gradient.
	 *
	 * <p>Uses the vanilla {@code fillGradient} pipeline, so the interpolation happens on the GPU and the
	 * call costs one quad rather than one per scanline.</p>
	 *
	 * @param topArgb    colour at {@code y}
	 * @param bottomArgb colour at {@code y + height}
	 */
	public static void drawGradientRect(DrawContext ctx, float x, float y, float width, float height,
										int topArgb, int bottomArgb) {
		if (width <= 0.0f || height <= 0.0f) {
			return;
		}

		int x1 = Math.round(x);
		int y1 = Math.round(y);
		int x2 = Math.round(x + width);
		int y2 = Math.round(y + height);

		if (x2 <= x1 || y2 <= y1) {
			return;
		}

		ctx.fillGradient(x1, y1, x2, y2, topArgb, bottomArgb);
	}

	/**
	 * Rounded rectangle filled with a vertical gradient.
	 *
	 * <p>Built from horizontal bands rather than one quad: each band's width is inset by the corner
	 * circle at its own rows, so the shape keeps its rounded ends while the colour travels.</p>
	 *
	 * <p>Each band is filled with a plain {@link #drawRect}. {@code DrawContext.fillGradient} looks like the
	 * cheaper choice — one GPU-interpolated quad instead of a flat fill — but it selects a different render
	 * pipeline, and the GUI renderer merges elements into a batch only while the pipeline, texture and scissor
	 * all match. Every gradient band therefore ends the running batch of ordinary fills, and with eight bands on
	 * each of roughly twenty glass panels that is over a hundred batch breaks per frame. Measured on a full HUD
	 * it cost more than the fills it saved, which is why the bands are flat fills again.</p>
	 *
	 * @param steps number of gradient bands, clamped to the pixel height
	 */
	public static void drawRoundedGradient(DrawContext ctx, float x, float y, float width, float height,
										   float radius, int topArgb, int bottomArgb, int steps) {
		if (width <= 0.0f || height <= 0.0f) {
			return;
		}

		float r = Math.min(radius, Math.min(width, height) * 0.5f);

		if (r < 1.0f) {
			drawGradientRect(ctx, x, y, width, height, topArgb, bottomArgb);
			return;
		}

		int bands = Math.max(1, Math.min(steps, Math.max(1, Math.round(height))));
		float bandHeight = height / bands;
		int lastRow = Math.round(y + height);

		for (int band = 0; band < bands; band++) {
			int rowStart = Math.round(y + band * bandHeight);
			int rowEnd = band == bands - 1 ? lastRow : Math.round(y + (band + 1) * bandHeight);

			if (rowEnd <= rowStart) {
				continue;
			}

			// The corner arc is only present near the top and bottom edges, so the inset is the deeper of
			// the two arcs at the rows this band covers.
			float inset = Math.max(cornerInset(rowStart - y, r), cornerInset(y + height - rowEnd, r));
			// Colour is sampled at the band's centre so the gradient is symmetric about the panel.
			float position = bands == 1 ? 0.0f : (band + 0.5f) / bands;

			drawRect(ctx, x + inset, rowStart, width - inset * 2.0f, rowEnd - rowStart,
					mix(topArgb, bottomArgb, position));
		}
	}

	/** Horizontal inset of a rounded corner at {@code distance} pixels into the arc. */
	private static float cornerInset(float distance, float radius) {
		if (distance >= radius) {
			return 0.0f;
		}

		float clamped = Math.max(0.0f, distance);
		double reach = radius - clamped;
		double half = Math.sqrt(Math.max(0.0, (double) radius * radius - reach * reach));
		return (float) (radius - half);
	}

	/**
	 * Draws a soft shadow behind a rounded rectangle.
	 *
	 * <p>The GUI API has no blur, so the shadow is a stack of concentric rounded rectangles: the outermost
	 * is the largest and faintest, and each layer inward both shrinks and strengthens. Stacking them makes
	 * the alpha accumulate toward the panel, which reads as a penumbra rather than as rings.</p>
	 *
	 * <p>The stack is capped at {@link #MAX_SHADOW_LAYERS}. Each layer is a full rounded rectangle — a couple
	 * of dozen quads — and a HUD with a dozen glass panels would otherwise spend thousands of them per frame on
	 * shadow alone, which is the largest single cost in the HUD's draw path. Two layers is where the extra
	 * layers stop being visible: a third and fourth only repeat, at low alpha, what the first two already
	 * painted. The alpha curve is evaluated against the layer count actually drawn, so the two surviving
	 * layers still run from faint at the edge to solid at the panel.</p>
	 *
	 * @param spread how far the shadow reaches, in GUI pixels
	 */
	public static void drawSoftShadow(DrawContext ctx, float x, float y, float width, float height,
									  float radius, float spread, int argb) {
		int baseAlpha = argb >>> 24;

		if (baseAlpha == 0) {
			return;
		}

		if (GradientDiagnostics.LEGACY) {
			// The uncapped stack, exactly as it was before the shadow was optimised: `spread` layers of
			// decreasing size and increasing strength.
			int legacyLayers = Math.max(1, Math.round(spread));

			for (int layer = legacyLayers; layer >= 1; layer--) {
				float falloff = 1.0f - layer / (float) legacyLayers;
				int alpha = Math.round(baseAlpha * falloff * falloff * 0.6f);

				if (alpha <= 1) {
					continue;
				}

				drawRoundedRect(ctx, x - layer, y - layer, width + layer * 2.0f, height + layer * 2.0f,
						radius + layer, withAlpha(argb, alpha));
			}

			return;
		}

		int layers = Math.max(1, Math.min(MAX_SHADOW_LAYERS, Math.round(spread)));
		float step = spread / layers;

		for (int layer = layers; layer >= 1; layer--) {
			float grow = layer * step;
			float falloff = 1.0f - layer / (float) layers;
			int alpha = Math.max(1, Math.min(255, Math.round(baseAlpha * falloff * falloff * 0.6f)));

			drawRoundedRect(ctx, x - grow, y - grow, width + grow * 2.0f, height + grow * 2.0f,
					radius + grow, withAlpha(argb, alpha));
		}
	}

	/** {@link #drawSoftShadow} under a different name, for accent-coloured halos. */
	public static void drawGlow(DrawContext ctx, float x, float y, float width, float height,
								float radius, float spread, int argb) {
		drawSoftShadow(ctx, x, y, width, height, radius, spread, argb);
	}

	/**
	 * The standard Sakura panel: one flat, slightly translucent card with a hairline edge.
	 *
	 * <p>Every HUD element and window draws through this so the whole client reads as one material. The blur
	 * behind it is provided by the screen itself (vanilla applies it once per frame from
	 * {@code Screen.renderBackground}); this method supplies the card that sits on top of that blur.</p>
	 *
	 * <p>The material is flat by design. It used to be a stack — shadow, twenty-band gradient, sheen, inner
	 * highlight and border — which cost roughly a hundred and fifty fills for every panel on screen: with a
	 * dozen elements and seven key caps in the keystrokes overlay that ran into thousands of quads per frame,
	 * and the HUD was measured at 10 fps with everything switched on. A single fill plus a hairline reads almost
	 * identically against the blurred backdrop at a fraction of the cost, and the depth now comes from the
	 * surface colours rather than from accumulated alpha.</p>
	 *
	 * <p>{@code shadowArgb} and {@code shadowSpread} are still accepted so no call site has to change; the flat
	 * material ignores them. {@link #drawSoftShadow} and {@link #drawAccentWash} remain for the few places that
	 * genuinely want depth — an album-art halo, a target flash.</p>
	 *
	 * @param tintTop      the panel's colour; the flat material has no gradient, so this is most of it
	 * @param tintBottom   the colour at the bottom edge, kept so callers need not change; averaged in
	 * @param borderArgb   hairline outline, usually a low-alpha white
	 * @param shadowArgb   shadow colour including its own alpha; ignored by the flat material
	 * @param shadowSpread shadow reach in pixels; ignored by the flat material
	 */
	public static void drawGlassPanel(DrawContext ctx, float x, float y, float width, float height, float radius,
									  int tintTop, int tintBottom, int borderArgb, int shadowArgb,
									  float shadowSpread) {
		if (width <= 0.0f || height <= 0.0f) {
			return;
		}

		// One solid, slightly translucent card: a single fill. No gradient, no sheen, no inner highlight and no
		// shadow — the flat material replaces all four with colour alone, which is both the look and the reason
		// it is cheap. The two tints are averaged so a caller that picked them for contrast still gets the mean
		// weight rather than only the top half of its own palette.
		drawRoundedRect(ctx, x, y, width, height, radius, mix(tintTop, tintBottom, 0.5f));

		// The hairline is skipped on small surfaces such as a key cap, where it would sit on top of the text
		// for no visual gain and cost more than the fill it outlines.
		if (width >= MIN_BORDER_WIDTH && (borderArgb >>> 24) != 0) {
			drawBorder(ctx, x, y, width, height, radius, 1.0f, borderArgb);
		}
	}

	/**
	 * Draws an accent stripe that bleeds the given colour across the top of a panel.
	 *
	 * <p>Fades downward instead of ending on a hard line, which is what keeps an accent from looking like
	 * a border.</p>
	 *
	 * <p><b>Currently a no-op.</b> The flat material carries no tint over its body: the accent's job — telling
	 * the player which colour the client is running — is done by the accent used elsewhere (the switch fill, the
	 * selection rows, the HUD stripes), and the wash itself was eight gradient bands on each of a dozen
	 * elements, which is far too much to spend on a tint that was barely visible against a blurred backdrop.
	 * The method is kept so the call sites in the HUD elements stay readable and so a future design that wants
	 * the tint back has an obvious place to put it.</p>
	 */
	public static void drawAccentWash(DrawContext ctx, float x, float y, float width, float height,
									  float radius, int accentArgb, float strength) {
	}

	// ---------------------------------------------------- progress & indicators

	/**
	 * A rounded progress bar: track plus a filled portion.
	 *
	 * @param progress 0..1, clamped
	 */
	public static void drawProgressBar(DrawContext ctx, float x, float y, float width, float height,
									   float progress, int trackArgb, int fillArgb) {
		if (width <= 0.0f || height <= 0.0f) {
			return;
		}

		float clamped = progress < 0.0f ? 0.0f : (progress > 1.0f ? 1.0f : progress);
		float radius = height * 0.5f;

		drawRoundedRect(ctx, x, y, width, height, radius, trackArgb);

		float filled = width * clamped;

		// A rounded cap cannot be drawn narrower than its own radius, so tiny amounts are drawn as the cap.
		if (filled > 0.01f) {
			drawRoundedRect(ctx, x, y, Math.max(filled, height), height, radius, fillArgb);
		}
	}

	// ------------------------------------------------------------- textures

	/** Draws a whole texture stretched into the given rectangle. */
	public static void drawTextureQuad(DrawContext ctx, Identifier texture, float x, float y,
									   float width, float height) {
		if (width <= 0.0f || height <= 0.0f) {
			return;
		}

		int w = Math.max(1, Math.round(width));
		int h = Math.max(1, Math.round(height));
		// Texture size equals the drawn size with a zero UV origin, which maps the whole texture onto the quad.
		ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture, Math.round(x), Math.round(y), 0.0f, 0.0f,
				w, h, w, h);
	}

	/**
	 * Draws a texture with rounded corners.
	 *
	 * <p>The GUI API cannot clip a texture to a rounded shape, so the rows that make up the corner arcs are
	 * drawn one pixel tall with a scissor box that follows the circle. Only the corner rows need that
	 * treatment — the straight middle is a single scissored quad — so the cost stays at roughly
	 * {@code 2 * radius} draws regardless of how tall the image is.</p>
	 */
	public static void drawRoundedTexture(DrawContext ctx, Identifier texture, float x, float y,
										  float width, float height, float radius) {
		float r = Math.min(radius, Math.min(width, height) * 0.5f);

		if (r < 1.0f) {
			drawTextureQuad(ctx, texture, x, y, width, height);
			return;
		}

		// Straight middle band.
		ctx.enableScissor(Math.round(x), Math.round(y + r), Math.round(x + width), Math.round(y + height - r));
		drawTextureQuad(ctx, texture, x, y, width, height);
		ctx.disableScissor();

		int rows = Math.max(1, Math.round(r));

		for (int row = 0; row < rows; row++) {
			double dy = r - (row + 0.5);
			float inset = (float) (r - Math.sqrt(Math.max(0.0, (double) r * r - dy * dy)));
			int left = Math.round(x + inset);
			int right = Math.round(x + width - inset);

			if (right <= left) {
				continue;
			}

			int topRow = Math.round(y + row);
			ctx.enableScissor(left, topRow, right, topRow + 1);
			drawTextureQuad(ctx, texture, x, y, width, height);
			ctx.disableScissor();

			int bottomRow = Math.round(y + height - row - 1.0f);
			ctx.enableScissor(left, bottomRow, right, bottomRow + 1);
			drawTextureQuad(ctx, texture, x, y, width, height);
			ctx.disableScissor();
		}
	}

	// --------------------------------------------------- scrolling text

	/**
	 * Draws text that scrolls horizontally when it is wider than its box.
	 *
	 * <p>The text scrolls right-to-left, pauses at each end, and is clipped to the box with a scissor, so
	 * long titles stay readable without shrinking the font. Callers keep the phase in a float field and
	 * advance it with the frame delta.</p>
	 *
	 * @param offset  pixels the text has scrolled so far, normally {@code >= 0}
	 */
	public static void drawMarqueeText(DrawContext ctx, String text, float x, float y, float boxWidth,
									   float offset, int argb, boolean shadow) {
		if (text == null || text.isEmpty() || boxWidth <= 0.0f) {
			return;
		}

		float textWidth = textWidth(text);

		if (textWidth <= boxWidth) {
			drawText(ctx, text, x, y, argb, shadow);
			return;
		}

		ctx.enableScissor(Math.round(x), Math.round(y), Math.round(x + boxWidth),
				Math.round(y + fontHeight() + 1.0f));
		drawText(ctx, text, x - offset, y, argb, shadow);
		ctx.disableScissor();
	}

	// ------------------------------------------------- world overlay primitives

	/**
	 * Draws a rectangular outline of the given thickness around the rectangle spanned by two corners.
	 *
	 * <p>Built from four {@link #drawRect} calls, so it stays inside the GUI API and needs nothing from the
	 * (much heavier) 3D render pipeline.</p>
	 */
	public static void drawOutline(DrawContext ctx, float x0, float y0, float x1, float y1,
								   float thickness, int argb) {
		if ((argb >>> 24) == 0) {
			return;
		}

		float left = Math.min(x0, x1);
		float right = Math.max(x0, x1);
		float top = Math.min(y0, y1);
		float bottom = Math.max(y0, y1);
		float t = Math.max(1.0f, thickness);
		float width = right - left;
		float height = bottom - top;

		if (width <= 0.0f || height <= 0.0f) {
			return;
		}

		drawRect(ctx, left, top, width, t, argb);
		drawRect(ctx, left, bottom - t, width, t, argb);

		// The vertical edges only span what the horizontal ones left over, so corners are not drawn twice.
		float innerHeight = height - 2.0f * t;

		if (innerHeight > 0.0f) {
			drawRect(ctx, left, top + t, t, innerHeight, argb);
			drawRect(ctx, right - t, top + t, t, innerHeight, argb);
		}
	}

	/** Draws an outline made of four corner brackets, the style most 2D ESPs use. */
	public static void drawCornerBox(DrawContext ctx, float x0, float y0, float x1, float y1,
									 float length, float thickness, int argb) {
		if ((argb >>> 24) == 0) {
			return;
		}

		float left = Math.min(x0, x1);
		float right = Math.max(x0, x1);
		float top = Math.min(y0, y1);
		float bottom = Math.max(y0, y1);
		float t = Math.max(1.0f, thickness);
		float arm = Math.min(length, Math.min(right - left, bottom - top) * 0.5f);

		if (arm <= 0.0f) {
			return;
		}

		// Top left, top right, bottom left, bottom right: a horizontal and a vertical arm each.
		drawRect(ctx, left, top, arm, t, argb);
		drawRect(ctx, left, top + t, t, arm - t, argb);
		drawRect(ctx, right - arm, top, arm, t, argb);
		drawRect(ctx, right - t, top + t, t, arm - t, argb);
		drawRect(ctx, left, bottom - t, arm, t, argb);
		drawRect(ctx, left, bottom - arm, t, arm - t, argb);
		drawRect(ctx, right - arm, bottom - t, arm, t, argb);
		drawRect(ctx, right - t, bottom - arm, t, arm - t, argb);
	}

	/**
	 * Draws a straight line of the given thickness by stamping a square brush along it.
	 *
	 * <p>The GUI API only fills axis-aligned rectangles, so a rotated line has to be assembled from them. The
	 * segment is first clipped to the window (Liang-Barsky), which both keeps the brush count bounded for a
	 * tracer that points off screen and avoids stamping thousands of squares at nothing.</p>
	 */
	public static void drawLine(DrawContext ctx, float x0, float y0, float x1, float y1,
								float thickness, int argb) {
		if ((argb >>> 24) == 0) {
			return;
		}

		if (!clipSegment(x0, y0, x1, y1, 0.0f, 0.0f,
				ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight())) {
			return;
		}

		// clipSegment leaves the visible part of the segment in that scratch array.
		float clipX0 = clipScratch[0];
		float clipY0 = clipScratch[1];
		float clipX1 = clipScratch[2];
		float clipY1 = clipScratch[3];

		float t = Math.max(1.0f, thickness);		float dx = clipX1 - clipX0;
		float dy = clipY1 - clipY0;
		int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) / t);

		if (steps <= 0) {
			drawRect(ctx, clipX0 - t * 0.5f, clipY0 - t * 0.5f, t, t, argb);
			return;
		}

		for (int step = 0; step <= steps; step++) {
			float progress = (float) step / steps;
			drawRect(ctx, clipX0 + dx * progress - t * 0.5f, clipY0 + dy * progress - t * 0.5f, t, t, argb);
		}
	}

	/** Scratch for {@link #clipSegment}, reused so drawing never allocates. */
	private static final float[] clipScratch = new float[4];

	/** Liang-Barsky parameters, reused for the same reason. */
	private static final float[] clipP = new float[4];
	private static final float[] clipQ = new float[4];

	/**
	 * Clips a segment against a rectangle.
	 *
	 * @return {@code true} when any part of the segment is inside, in which case the clipped endpoints are
	 * left in {@link #clipScratch}
	 */
	private static boolean clipSegment(float x0, float y0, float x1, float y1,
									   float left, float top, float right, float bottom) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float start = 0.0f;
		float end = 1.0f;

		clipP[0] = -dx;
		clipP[1] = dx;
		clipP[2] = -dy;
		clipP[3] = dy;
		clipQ[0] = x0 - left;
		clipQ[1] = right - x0;
		clipQ[2] = y0 - top;
		clipQ[3] = bottom - y0;

		for (int edge = 0; edge < 4; edge++) {
			if (clipP[edge] == 0.0f) {
				if (clipQ[edge] < 0.0f) {
					return false;
				}

				continue;
			}

			float ratio = clipQ[edge] / clipP[edge];

			if (clipP[edge] < 0.0f) {
				if (ratio > end) {
					return false;
				}

				if (ratio > start) {
					start = ratio;
				}
			} else {
				if (ratio < start) {
					return false;
				}

				if (ratio < end) {
					end = ratio;
				}
			}
		}

		clipScratch[0] = x0 + start * dx;
		clipScratch[1] = y0 + start * dy;
		clipScratch[2] = x0 + end * dx;
		clipScratch[3] = y0 + end * dy;

		return true;
	}
}
