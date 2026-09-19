package com.sakura.client.rotation;

import com.sakura.client.setting.Tagged;

/**
 * How a rotation travels from where the player is looking to where the module wants to look.
 *
 * <p>The names match LiquidBounce's smooth rotation modes. Each mode maps elapsed progress onto the fraction of
 * the distance covered, so all three end up at exactly the same place 鈥?they differ only in how the first ticks
 * of the turn feel, which is what a server-side rotation check notices.</p>
 */
public enum RotationMode implements Tagged {

	/** Constant degrees per tick: the fastest of the three and the easiest to spot. */
	LINEAR("Linear") {
		@Override
		public double ease(double progress) {
			return progress;
		}
	},

	/** Slow start, fast middle, slow end: the closest to how a hand actually moves a mouse. */
	INTERPOLATION("Interpolation") {
		@Override
		public double ease(double progress) {
			return progress * progress * (3.0 - 2.0 * progress);
		}
	},

	/** A steeper curve than Interpolation, with a flatter start and a harder stop. */
	SIGMOID("Sigmoid") {
		@Override
		public double ease(double progress) {
			return (sigmoid(progress) - sigmoid(0.0)) / (sigmoid(1.0) - sigmoid(0.0));
		}

		private double sigmoid(double value) {
			return 1.0 / (1.0 + Math.exp(-10.0 * (value - 0.5)));
		}
	};

	private final String label;

	RotationMode(String label) {
		this.label = label;
	}

	/** @param progress elapsed fraction of the turn, 0 to 1 @return fraction of the distance covered */
	public abstract double ease(double progress);

	@Override
	public String getTag() {
		return this.label;
	}
}
