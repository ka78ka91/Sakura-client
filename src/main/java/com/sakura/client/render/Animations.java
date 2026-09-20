package com.sakura.client.render;

/**
 * Easing curves, colour interpolation and frame-rate independent smoothing for the Sakura UI.
 *
 * <p>Everything here is pure math so the drawing code stays declarative: an element keeps a float, asks
 * for the target value each frame and lets {@link #approach} travel there at a speed expressed in
 * "how fast in wall-clock time", not "how much per frame". That matters because the HUD renders at the
 * monitor's refresh rate while the game ticks at 20 Hz, and a per-frame step would animate four times
 * faster on a 240 Hz screen than on a 60 Hz one.</p>
 */
public final class Animations {

	private Animations() {
	}

	// ---------------------------------------------------------------- clamps

	public static float clamp01(float value) {
		return value < 0.0f ? 0.0f : (value > 1.0f ? 1.0f : value);
	}

	public static float clamp(float value, float min, float max) {
		return value < min ? min : (value > max ? max : value);
	}

	public static float lerp(float from, float to, float delta) {
		return from + (to - from) * delta;
	}

	// ---------------------------------------------------------------- easing

	/** Symmetric ease, cheap and neutral: the default for fades and slides. */
	public static float smoothStep(float t) {
		float x = clamp01(t);
		return x * x * (3.0f - 2.0f * x);
	}

	/** Fast start, gentle stop. Used for anything entering the screen. */
	public static float easeOutCubic(float t) {
		float x = clamp01(t);
		float inverse = 1.0f - x;
		return 1.0f - inverse * inverse * inverse;
	}

	/** Very fast start, very gentle stop: the "expensive" feel for menus and toasts. */
	public static float easeOutQuint(float t) {
		float x = clamp01(t);
		float inverse = 1.0f - x;
		return 1.0f - inverse * inverse * inverse * inverse * inverse;
	}

	public static float easeInOutCubic(float t) {
		float x = clamp01(t);

		if (x < 0.5f) {
			return 4.0f * x * x * x;
		}

		float inverse = -2.0f * x + 2.0f;
		return 1.0f - inverse * inverse * inverse / 2.0f;
	}

	/** Overshoots slightly before settling: gives switches and bars a springy pop. */
	public static float easeOutBack(float t) {
		float x = clamp01(t);
		float overshoot = 1.70158f;
		float shifted = x - 1.0f;
		return 1.0f + (overshoot + 1.0f) * shifted * shifted * shifted + overshoot * shifted * shifted;
	}

	/** Elastic settle, for one-off flourishes. Never leaves the 0..1 range by much, so it is safe to use as an alpha. */
	public static float easeOutElastic(float t) {
		float x = clamp01(t);

		if (x <= 0.0f || x >= 1.0f) {
			return x;
		}

		double period = 2.0 * Math.PI / 3.0;
		return (float) (Math.pow(2.0, -10.0 * x) * Math.sin((x * 10.0 - 0.75) * period) + 1.0);
	}

	// ------------------------------------------------------------- smoothing

	/**
	 * Moves {@code current} toward {@code target} at a speed measured in wall-clock time.
	 *
	 * <p>Exponential rather than linear, so the motion decelerates naturally as it arrives and never
	 * overshoots. {@code speed} is roughly "how many e-folds per second": 6 is a quick UI response,
	 * 12 is snappy, 2 is a slow drift.</p>
	 *
	 * @param deltaSeconds frame time, from {@link Clock#tick()}
	 */
	public static float approach(float current, float target, float speed, float deltaSeconds) {
		if (speed <= 0.0f || deltaSeconds <= 0.0f) {
			return target;
		}

		float factor = 1.0f - (float) Math.exp(-speed * deltaSeconds);
		return current + (target - current) * factor;
	}

	/**
	 * Same as {@link #approach} but snaps to the target once the remaining distance is under
	 * {@code epsilon}, so a value can actually reach 0 and stop redrawing.
	 */
	public static float approach(float current, float target, float speed, float deltaSeconds, float epsilon) {
		float next = approach(current, target, speed, deltaSeconds);
		return Math.abs(target - next) <= epsilon ? target : next;
	}

	/** A 0..1..0 curve driven by the wall clock; used for glows and breathing highlights. */
	public static float breathe(long periodMillis, float phase) {
		if (periodMillis <= 0L) {
			return 0.5f;
		}

		double angle = (System.currentTimeMillis() % periodMillis) / (double) periodMillis * Math.PI * 2.0 + phase;
		return (float) ((Math.sin(angle) + 1.0) * 0.5);
	}

	/** A plain 0..1 sawtooth; useful for shimmer sweeps. */
	public static float saw(long periodMillis) {
		if (periodMillis <= 0L) {
			return 0.0f;
		}

		return (System.currentTimeMillis() % periodMillis) / (float) periodMillis;
	}

	/**
	 * Wall-clock frame delta, shared by the elements that animate.
	 *
	 * <p>The delta is capped at 100 ms so that a stall (a world load, a garbage collection pause, the
	 * window being dragged) cannot teleport an animation to its end value in a single frame.</p>
	 */
	public static final class Clock {

		private long last = System.nanoTime();

		public float tick() {
			long now = System.nanoTime();
			float delta = (now - this.last) / 1_000_000_000.0f;
			this.last = now;
			return Math.min(0.1f, Math.max(0.0f, delta));
		}

		/** Restarts the clock, so the first frame after a long pause is not treated as a huge delta. */
		public void reset() {
			this.last = System.nanoTime();
		}
	}
}
