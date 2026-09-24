package com.sakura.client.gui.widget;

import com.sakura.client.render.Animations;
import com.sakura.client.render.RenderUtils;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;

/**
 * Capsule switch (36x20) with a smoothly animated knob.
 *
 * <p>The knob position is an animation factor rather than a boolean, so the transition is an exponential
 * approach towards the target driven by the frame delta —the same {@code transition: 0.3s} feel of the
 * prototype, but it plays out identically on every refresh rate.</p>
 */
public class ToggleWidget implements GuiWidget {

	private static final float WIDTH = 36.0f;
	private static final float HEIGHT = 20.0f;
	private static final float RADIUS = HEIGHT / 2.0f;
	private static final float KNOB_RADIUS = 7.0f;
	private static final float KNOB_INSET = 3.0f;
	/** E-folds per second, matching the feel of the 0.2-per-frame factor the widget used at 60 fps. */
	private static final float ANIMATION_SPEED = 13.4f;
	private static final float ANIMATION_EPSILON = 0.001f;

	private static final int ON_COLOR = 0xFFA06EFF;
	private static final int OFF_COLOR = 0x1AFFFFFF;
	private static final int KNOB_COLOR = 0xFFFFFFFF;
	private static final int OUTLINE = 0x14FFFFFF;

	private final Animations.Clock clock = new Animations.Clock();

	private boolean on;
	private float animation;
	private Runnable onToggle;

	private float x;
	private float y;

	public ToggleWidget(boolean on) {
		this.on = on;
		this.animation = on ? 1.0f : 0.0f;
	}

	public static float widgetWidth() {
		return WIDTH;
	}

	public static float widgetHeight() {
		return HEIGHT;
	}

	public boolean isOn() {
		return on;
	}

	public void setOn(boolean on) {
		this.on = on;
	}

	/** Invoked after the user flips the switch, so the owner can mirror the new state. */
	public void setOnToggle(Runnable onToggle) {
		this.onToggle = onToggle;
	}

	public void toggle() {
		this.on = !this.on;

		if (this.onToggle != null) {
			this.onToggle.run();
		}
	}

	/** Draws the switch with its top-left corner at ({@code x}, {@code y}). */
	public void draw(DrawContext context, float x, float y) {
		this.x = x;
		this.y = y;

		float target = this.on ? 1.0f : 0.0f;

		// Exponential approach over the wall-clock delta, so the knob arrives at the same moment on any
		// refresh rate; a per-frame factor animates four times faster on a 240 Hz screen than on 60 Hz.
		this.animation = Animations.approach(this.animation, target, ANIMATION_SPEED, this.clock.tick(),
				ANIMATION_EPSILON);

		RenderUtils.drawRoundedRect(context, x, y, WIDTH, HEIGHT, RADIUS, lerpColor(OFF_COLOR, ON_COLOR, this.animation));
		RenderUtils.drawBorder(context, x, y, WIDTH, HEIGHT, RADIUS, 1.0f, OUTLINE);

		float travel = WIDTH - 2.0f * KNOB_INSET - KNOB_RADIUS * 2.0f;
		float knobCenterX = x + KNOB_INSET + KNOB_RADIUS + travel * this.animation;
		float knobCenterY = y + HEIGHT / 2.0f;

		RenderUtils.drawRoundedRect(context, knobCenterX - KNOB_RADIUS, knobCenterY - KNOB_RADIUS,
				KNOB_RADIUS * 2.0f, KNOB_RADIUS * 2.0f, KNOB_RADIUS, KNOB_COLOR);
	}

	// -------------------------------------------------------------------- input

	public boolean mouseClicked(Click click) {
		if (!isHovered(click.x(), click.y())) {
			return false;
		}

		toggle();
		return true;
	}

	private boolean isHovered(double mouseX, double mouseY) {
		return mouseX >= this.x && mouseX < this.x + WIDTH
				&& mouseY >= this.y && mouseY < this.y + HEIGHT;
	}

	// ------------------------------------------------------------------- colour

	private static int lerpColor(int from, int to, float factor) {
		float t = Math.max(0.0f, Math.min(1.0f, factor));

		int a = lerpChannel(from >>> 24, to >>> 24, t);
		int r = lerpChannel((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
		int g = lerpChannel((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
		int b = lerpChannel(from & 0xFF, to & 0xFF, t);

		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static int lerpChannel(int from, int to, float t) {
		return Math.round(from + (to - from) * t);
	}
}
