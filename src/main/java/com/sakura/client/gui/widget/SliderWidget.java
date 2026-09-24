package com.sakura.client.gui.widget;


import com.sakura.client.render.Theme;
import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.RenderUtils.Align;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;

/**
 * Minimal horizontal slider: a 4px rounded track with a round white knob.
 *
 * <p>The widget stores its value as a normalised {@code 0..1} factor so one instance can drive
 * "16 px", "85%" or any other range through {@link #map(float, float)}. Its hit box is recorded by
 * {@link #draw}, which the screen always calls before dispatching input for the frame.</p>
 */
public class SliderWidget implements GuiWidget {

	private static final float TRACK_HEIGHT = 4.0f;
	private static final float TRACK_RADIUS = 2.0f;
	private static final float KNOB_RADIUS = 6.0f;
	private static final float ROW_HEIGHT = 20.0f;
	private static final float LABEL_GAP = 10.0f;
	private static final float DEFAULT_TRACK_WIDTH = 160.0f;
	private float value;
	private boolean dragging;

	private float trackX;
	private float trackY;
	private float trackWidth;
	private float rowY;

	public SliderWidget(float value) {
		setValue(value);
	}

	public float getValue() {
		return value;
	}

	public void setValue(float value) {
		this.value = Math.max(0.0f, Math.min(1.0f, value));
	}

	/** The normalised value projected onto a concrete range, e.g. {@code map(0, 50)} -> {@code 16.0}. */
	public float map(float min, float max) {
		return min + (max - min) * this.value;
	}

	/** Sets the slider from a concrete range value, e.g. {@code setFromRange(16, 0, 50)}. */
	public void setFromRange(float rangeValue, float min, float max) {
		if (max - min == 0.0f) {
			setValue(0.0f);
			return;
		}

		setValue((rangeValue - min) / (max - min));
	}

	public boolean isDragging() {
		return dragging;
	}

	/**
	 * Draws one label/slider/value row and caches the track bounds for hit testing.
	 *
	 * <p>The row is laid out left to right — label, {@link #LABEL_GAP}, track, {@link #LABEL_GAP}, value —
	 * so the track can never be pushed onto the caption. It used to be measured from the right only, which
	 * left the caption width out of the calculation entirely: with a wide caption or a narrow row the track
	 * started inside the text. The track is clamped to {@code [0, DEFAULT_TRACK_WIDTH]} and may fall below its
	 * usual floor on a cramped row, because a short track is a much smaller problem than an unreadable
	 * caption.</p>
	 *
	 * @param label     left-hand caption, may be {@code null}
	 * @param valueText right-hand read-out such as {@code "16 px"}, may be {@code null}
	 */
	public void draw(DrawContext context, float x, float y, float width, String label, String valueText) {
		this.rowY = y;

		float labelWidth = label == null ? 0.0f : RenderUtils.textWidth(label);
		float valueWidth = valueText == null ? 0.0f : RenderUtils.textWidth(valueText);

		// One gap after the caption and one before the read-out; a side that draws nothing reserves nothing.
		float leftInset = label == null ? 0.0f : labelWidth + LABEL_GAP;
		float rightInset = valueWidth + (valueText == null ? 0.0f : LABEL_GAP);
		float room = width - leftInset - rightInset;

		this.trackWidth = Math.max(0.0f, Math.min(DEFAULT_TRACK_WIDTH, room));
		// Centred in whatever is left over once both insets are taken, so a short track does not sit hard
		// against the caption.
		this.trackX = x + leftInset + Math.max(0.0f, room - this.trackWidth) / 2.0f;
		this.trackY = y + (ROW_HEIGHT - TRACK_HEIGHT) / 2.0f + 1.0f;

		if (label != null) {
			RenderUtils.drawTextVCentered(context, label, x, y, ROW_HEIGHT, Theme.text(), false, Align.LEFT);
		}

		if (valueText != null) {
			RenderUtils.drawTextVCentered(context, valueText, x + width, y, ROW_HEIGHT, Theme.textDim(), false, Align.RIGHT);
		}

		if (this.trackWidth <= 0.0f) {
			// No room for a track at all: the caption and the read-out are still worth drawing.
			return;
		}

		RenderUtils.drawRoundedRect(context, this.trackX, this.trackY, this.trackWidth, TRACK_HEIGHT,
				TRACK_RADIUS, Theme.buttonBg());

		float filled = this.trackWidth * this.value;
		if (filled > 0.0f) {
			RenderUtils.drawRoundedRect(context, this.trackX, this.trackY, filled, TRACK_HEIGHT,
					TRACK_RADIUS, Theme.text());
		}

		float knobCenterX = this.trackX + filled;
		float knobCenterY = this.trackY + TRACK_HEIGHT / 2.0f;
		RenderUtils.drawRoundedRect(context, knobCenterX - KNOB_RADIUS, knobCenterY - KNOB_RADIUS,
				KNOB_RADIUS * 2.0f, KNOB_RADIUS * 2.0f, KNOB_RADIUS, Theme.text());
	}

	// -------------------------------------------------------------------- input

	public boolean mouseClicked(Click click) {
		if (!isOverTrack(click.x(), click.y())) {
			return false;
		}

		this.dragging = true;
		updateFromMouse(click.x());
		return true;
	}

	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (!this.dragging) {
			return false;
		}

		updateFromMouse(click.x());
		return true;
	}

	public boolean mouseReleased(Click click) {
		if (!this.dragging) {
			return false;
		}

		this.dragging = false;
		return true;
	}

	private boolean isOverTrack(double mouseX, double mouseY) {
		return mouseX >= this.trackX - KNOB_RADIUS && mouseX <= this.trackX + this.trackWidth + KNOB_RADIUS
				&& mouseY >= this.rowY && mouseY <= this.rowY + ROW_HEIGHT;
	}

	private void updateFromMouse(double mouseX) {
		if (this.trackWidth <= 0.0f) {
			return;
		}

		setValue((float) ((mouseX - this.trackX) / this.trackWidth));
	}
}
