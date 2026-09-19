package com.sakura.client.gui.widget;

import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.RenderUtils.Align;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;

/**
 * Circular HSV colour wheel with two vertical value/alpha tracks and a HEX read-out.
 *
 * <p>The wheel is a hue/saturation disc: the angle around the centre picks the hue and the distance
 * from the centre picks the saturation, so the middle fades to white exactly like a radial
 * white overlay in CSS. It is rasterised from concentric rings of small axis-aligned spans because
 * the 1.21.11 GUI API only exposes {@link DrawContext#fill(int, int, int, int, int)} —no rotation
 * or shader access is needed.</p>
 */
public class ColorPickerWidget implements GuiWidget {

	private static final float WHEEL_RADIUS = 50.0f;
	private static final float WHEEL_DIAMETER = WHEEL_RADIUS * 2.0f;
	private static final float RING_STEP = 4.0f;
	private static final float TARGET_ARC = 7.0f;

	private static final float TRACK_WIDTH = 8.0f;
	private static final float TRACK_HEIGHT = 100.0f;
	private static final float TRACK_RADIUS = TRACK_WIDTH / 2.0f;
	private static final float TRACK_GAP = 12.0f;
	private static final float WHEEL_TRACK_GAP = 20.0f;
	private static final float KNOB_RADIUS = 6.0f;
	private static final float HEX_BOX_HEIGHT = 16.0f;
	private static final float HEX_BOX_GAP = 10.0f;
	private static final float MARKER_RADIUS = 4.0f;

	private static final int WHEEL_BACKDROP = 0x59000000;
	private static final int TRACK_BG = 0x1AFFFFFF;
	private static final int TRACK_FILL = 0xFFFFFFFF;
	private static final int KNOB = 0xFFFFFFFF;
	private static final int MARKER = 0xFFFFFFFF;
	private static final int HEX_BOX_BG = 0x4D000000;
	private static final int HEX_BOX_TEXT = 0xFFCCCCCC;

	private enum DragTarget {
		NONE,
		WHEEL,
		BRIGHTNESS,
		ALPHA
	}

	private float hue;
	private float saturation;
	private float brightness;
	private float alpha;

	private float wheelX;
	private float wheelY;
	private float brightnessTrackX;
	private float brightnessTrackY;
	private float alphaTrackX;
	private float alphaTrackY;
	private float hexBoxY;

	private DragTarget dragTarget = DragTarget.NONE;

	/** Defaults to the prototype accent, {@code #F3F3E8} at full alpha. */
	public ColorPickerWidget() {
		this(60.0f, 0.045f, 0.953f, 1.0f);
	}

	public ColorPickerWidget(float hue, float saturation, float brightness, float alpha) {
		this.hue = wrapHue(hue);
		this.saturation = clamp01(saturation);
		this.brightness = clamp01(brightness);
		this.alpha = clamp01(alpha);
	}

	/** Total width the widget occupies, including the tracks. */
	public static float widgetWidth() {
		return WHEEL_DIAMETER + WHEEL_TRACK_GAP + TRACK_WIDTH * 2.0f + TRACK_GAP;
	}

	/** Total height the widget occupies, including the HEX read-out. */
	public static float widgetHeight() {
		return WHEEL_DIAMETER + HEX_BOX_GAP + HEX_BOX_HEIGHT;
	}

	public float getHue() {
		return hue;
	}

	public float getSaturation() {
		return saturation;
	}

	public float getBrightness() {
		return brightness;
	}

	public float getAlpha() {
		return alpha;
	}

	/** Packed ARGB of the current selection, honouring alpha. */
	public int getColor() {
		return (Math.round(alpha * 255.0f) << 24) | (hsvToRgb(hue, saturation, brightness) & 0xFFFFFF);
	}

	/** Read-out in the prototype's format, e.g. {@code "#F3F3E8 100%"}. */
	public String getHex() {
		return String.format("#%06X %d%%", hsvToRgb(hue, saturation, brightness) & 0xFFFFFF,
				Math.round(alpha * 100.0f));
	}

	/** Selects a colour from a packed ARGB value, splitting it back into HSV + alpha. */
	public void setColor(int argb) {
		this.alpha = clamp01(((argb >>> 24) & 0xFF) / 255.0f);

		float red = ((argb >> 16) & 0xFF) / 255.0f;
		float green = ((argb >> 8) & 0xFF) / 255.0f;
		float blue = (argb & 0xFF) / 255.0f;

		float max = Math.max(red, Math.max(green, blue));
		float min = Math.min(red, Math.min(green, blue));
		float delta = max - min;

		this.brightness = clamp01(max);
		this.saturation = max <= 0.0f ? 0.0f : clamp01(delta / max);

		float computed;

		if (delta <= 0.0f) {
			computed = 0.0f;
		} else if (max == red) {
			computed = 60.0f * (((green - blue) / delta) % 6.0f);
		} else if (max == green) {
			computed = 60.0f * (((blue - red) / delta) + 2.0f);
		} else {
			computed = 60.0f * (((red - green) / delta) + 4.0f);
		}

		this.hue = wrapHue(computed);
	}

	/** Draws the widget with its top-left corner at ({@code x}, {@code y}). */
	public void draw(DrawContext context, float x, float y) {
		this.wheelX = x;
		this.wheelY = y;
		this.brightnessTrackX = x + WHEEL_DIAMETER + WHEEL_TRACK_GAP;
		this.brightnessTrackY = y;
		this.alphaTrackX = this.brightnessTrackX + TRACK_WIDTH + TRACK_GAP;
		this.alphaTrackY = y;
		this.hexBoxY = y + WHEEL_DIAMETER + HEX_BOX_GAP;

		drawWheel(context);
		drawTrack(context, this.brightnessTrackX, this.brightnessTrackY, this.brightness);
		drawTrack(context, this.alphaTrackX, this.alphaTrackY, this.alpha);
		drawHexBox(context);
	}

	private void drawWheel(DrawContext context) {
		float centerX = this.wheelX + WHEEL_RADIUS;
		float centerY = this.wheelY + WHEEL_RADIUS;

		RenderUtils.drawRoundedRect(context, this.wheelX, this.wheelY, WHEEL_DIAMETER, WHEEL_DIAMETER,
				WHEEL_RADIUS, WHEEL_BACKDROP);

		for (float outerRadius = WHEEL_RADIUS; outerRadius > 0.5f; outerRadius -= RING_STEP) {
			float innerRadius = Math.max(0.0f, outerRadius - RING_STEP);
			float midRadius = (outerRadius + innerRadius) / 2.0f;
			float ringHeight = outerRadius - innerRadius + 0.75f;
			int segments = Math.max(8, Math.round((float) (2.0 * Math.PI * midRadius / TARGET_ARC)));
			float arc = (float) (2.0 * Math.PI / segments);

			for (int i = 0; i < segments; i++) {
				double angle = i * arc;
				float segmentHue = (float) Math.toDegrees(angle);
				float segmentSaturation = midRadius / WHEEL_RADIUS;
				int color = hsvToArgb(segmentHue, segmentSaturation, this.brightness, 1.0f);

				float pointX = centerX + (float) Math.sin(angle) * midRadius;
				float pointY = centerY - (float) Math.cos(angle) * midRadius;
				float segmentWidth = Math.max(1.5f, midRadius * arc * 0.9f);

				RenderUtils.drawRect(context, pointX - segmentWidth / 2.0f, pointY - ringHeight / 2.0f,
						segmentWidth, ringHeight, color);
			}
		}

		drawMarker(context, centerX, centerY);
	}

	private void drawMarker(DrawContext context, float centerX, float centerY) {
		double angle = Math.toRadians(this.hue);
		float radius = this.saturation * WHEEL_RADIUS;
		float markerX = centerX + (float) Math.sin(angle) * radius;
		float markerY = centerY - (float) Math.cos(angle) * radius;

		RenderUtils.drawBorder(context, markerX - MARKER_RADIUS, markerY - MARKER_RADIUS,
				MARKER_RADIUS * 2.0f, MARKER_RADIUS * 2.0f, MARKER_RADIUS, 1.5f, MARKER);
	}

	private void drawTrack(DrawContext context, float trackX, float trackY, float value) {
		RenderUtils.drawRoundedRect(context, trackX, trackY, TRACK_WIDTH, TRACK_HEIGHT, TRACK_RADIUS, TRACK_BG);

		float filled = TRACK_HEIGHT * value;
		float top = trackY + TRACK_HEIGHT - filled;
		RenderUtils.drawRoundedRect(context, trackX, top, TRACK_WIDTH, filled, TRACK_RADIUS, TRACK_FILL);

		float knobCenterY = top;
		RenderUtils.drawRoundedRect(context, trackX + TRACK_WIDTH / 2.0f - KNOB_RADIUS, knobCenterY - KNOB_RADIUS,
				KNOB_RADIUS * 2.0f, KNOB_RADIUS * 2.0f, KNOB_RADIUS, KNOB);
	}

	private void drawHexBox(DrawContext context) {
		String hex = getHex();
		float boxWidth = RenderUtils.textWidth(hex) + 16.0f;

		RenderUtils.drawRoundedRect(context, this.wheelX, this.hexBoxY, boxWidth, HEX_BOX_HEIGHT, 4.0f, HEX_BOX_BG);
		RenderUtils.drawTextVCentered(context, hex, this.wheelX + boxWidth / 2.0f, this.hexBoxY, HEX_BOX_HEIGHT,
				HEX_BOX_TEXT, false, Align.CENTER);
	}

	// -------------------------------------------------------------------- input

	public boolean mouseClicked(Click click) {
		double mouseX = click.x();
		double mouseY = click.y();
		double centerX = this.wheelX + WHEEL_RADIUS;
		double centerY = this.wheelY + WHEEL_RADIUS;
		double distance = Math.hypot(mouseX - centerX, mouseY - centerY);

		if (distance <= WHEEL_RADIUS + 2.0) {
			this.dragTarget = DragTarget.WHEEL;
			updateFromWheel(mouseX, mouseY);
			return true;
		}

		if (isOverTrack(mouseX, mouseY, this.brightnessTrackX, this.brightnessTrackY)) {
			this.dragTarget = DragTarget.BRIGHTNESS;
			updateFromTrack(mouseY, this.brightnessTrackY, true);
			return true;
		}

		if (isOverTrack(mouseX, mouseY, this.alphaTrackX, this.alphaTrackY)) {
			this.dragTarget = DragTarget.ALPHA;
			updateFromTrack(mouseY, this.alphaTrackY, false);
			return true;
		}

		return false;
	}

	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		return switch (this.dragTarget) {
			case WHEEL -> {
				updateFromWheel(click.x(), click.y());
				yield true;
			}
			case BRIGHTNESS -> {
				updateFromTrack(click.y(), this.brightnessTrackY, true);
				yield true;
			}
			case ALPHA -> {
				updateFromTrack(click.y(), this.alphaTrackY, false);
				yield true;
			}
			case NONE -> false;
		};
	}

	public boolean mouseReleased(Click click) {
		if (this.dragTarget == DragTarget.NONE) {
			return false;
		}

		this.dragTarget = DragTarget.NONE;
		return true;
	}

	public boolean isDragging() {
		return this.dragTarget != DragTarget.NONE;
	}

	private boolean isOverTrack(double mouseX, double mouseY, float trackX, float trackY) {
		return mouseX >= trackX - KNOB_RADIUS && mouseX <= trackX + TRACK_WIDTH + KNOB_RADIUS
				&& mouseY >= trackY - KNOB_RADIUS && mouseY <= trackY + TRACK_HEIGHT + KNOB_RADIUS;
	}

	private void updateFromWheel(double mouseX, double mouseY) {
		double centerX = this.wheelX + WHEEL_RADIUS;
		double centerY = this.wheelY + WHEEL_RADIUS;
		double dx = mouseX - centerX;
		double dy = mouseY - centerY;
		double distance = Math.min(Math.hypot(dx, dy), WHEEL_RADIUS);

		this.saturation = clamp01((float) (distance / WHEEL_RADIUS));

		if (distance > 0.0001) {
			// Same mapping as the prototype's conic gradient: hue 0 at 12 o'clock, increasing clockwise.
			this.hue = wrapHue((float) Math.toDegrees(Math.atan2(dx, -dy)));
		}
	}

	private void updateFromTrack(double mouseY, float trackY, boolean isBrightness) {
		float value = clamp01((float) (1.0 - (mouseY - trackY) / TRACK_HEIGHT));

		if (isBrightness) {
			this.brightness = value;
		} else {
			this.alpha = value;
		}
	}

	// ------------------------------------------------------------------- maths

	private static float clamp01(float value) {
		return Math.max(0.0f, Math.min(1.0f, value));
	}

	private static float wrapHue(float value) {
		return ((value % 360.0f) + 360.0f) % 360.0f;
	}

	private static int hsvToArgb(float hue, float saturation, float brightness, float alpha) {
		return (Math.round(clamp01(alpha) * 255.0f) << 24) | (hsvToRgb(hue, saturation, brightness) & 0xFFFFFF);
	}

	private static int hsvToRgb(float hue, float saturation, float brightness) {
		float h = wrapHue(hue);
		float s = clamp01(saturation);
		float v = clamp01(brightness);

		float chroma = v * s;
		float secondary = chroma * (1.0f - Math.abs((h / 60.0f) % 2.0f - 1.0f));
		float match = v - chroma;

		float red;
		float green;
		float blue;

		if (h < 60.0f) {
			red = chroma;
			green = secondary;
			blue = 0.0f;
		} else if (h < 120.0f) {
			red = secondary;
			green = chroma;
			blue = 0.0f;
		} else if (h < 180.0f) {
			red = 0.0f;
			green = chroma;
			blue = secondary;
		} else if (h < 240.0f) {
			red = 0.0f;
			green = secondary;
			blue = chroma;
		} else if (h < 300.0f) {
			red = secondary;
			green = 0.0f;
			blue = chroma;
		} else {
			red = chroma;
			green = 0.0f;
			blue = secondary;
		}

		int r = Math.round((red + match) * 255.0f);
		int g = Math.round((green + match) * 255.0f);
		int b = Math.round((blue + match) * 255.0f);

		return (r << 16) | (g << 8) | b;
	}
}
