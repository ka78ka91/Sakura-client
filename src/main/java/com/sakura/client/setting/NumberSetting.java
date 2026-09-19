package com.sakura.client.setting;

/**
 * A number picked inside a fixed range, optionally snapped to a step and optionally integral.
 *
 * <p>Values are clamped on construction and on every {@link #set}, so a malformed config file cannot push a
 * parameter out of its legal range.</p>
 */
public class NumberSetting extends Setting<Double> {

	private final double min;
	private final double max;
	private final double step;
	private final boolean integral;
	private final String unit;

	public NumberSetting(String name, String description, double defaultValue,
						 double min, double max, double step, String unit) {
		super(name, description, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step <= 0.0 ? 0.0 : step;
		this.integral = this.step == 0.0 ? defaultValue == Math.rint(defaultValue) : this.step >= 1.0;
		this.unit = unit == null ? "" : unit;
		this.value = clamp(defaultValue);
	}

	public NumberSetting(String name, double defaultValue, double min, double max, double step, String unit) {
		this(name, "", defaultValue, min, max, step, unit);
	}

	@Override
	public SettingType type() {
		return SettingType.NUMBER;
	}

	public double getMin() {
		return this.min;
	}

	public double getMax() {
		return this.max;
	}

	public String getUnit() {
		return this.unit;
	}

	public boolean isIntegral() {
		return this.integral;
	}

	/** @return the current value rounded to an {@code int} when the setting is integral */
	public int intValue() {
		return (int) Math.round(get());
	}

	/** Fraction of the way from min to max, which is what the slider widget stores. */
	public float fraction() {
		if (this.max <= this.min) {
			return 0.0f;
		}

		return (float) ((get() - this.min) / (this.max - this.min));
	}

	public void setFraction(float fraction) {
		set(this.min + (this.max - this.min) * Math.clamp(fraction, 0.0f, 1.0f));
	}

	@Override
	public void set(Double newValue) {
		super.set(clamp(newValue));
	}

	private Double clamp(double raw) {
		double clamped = Math.clamp(raw, this.min, this.max);

		if (this.step > 0.0) {
			clamped = this.min + Math.round((clamped - this.min) / this.step) * this.step;
			clamped = Math.clamp(clamped, this.min, this.max);
			clamped = roundToStepPrecision(clamped);
		}

		if (this.integral) {
			clamped = Math.rint(clamped);
		}

		return clamped;
	}

	/**
	 * Rounds to the number of decimals the step is written in.
	 *
	 * <p>Seventeen steps of 0.05 land on 0.8500000000000001 in binary floating point. Rounding to the step's own
	 * precision turns that back into 0.85, which keeps stored values and the config file free of the noise.</p>
	 */
	private double roundToStepPrecision(double value) {
		double scale = 1.0;

		for (int decimals = 0; decimals < 6; decimals++) {
			if (Math.abs(this.step * scale - Math.rint(this.step * scale)) < 1.0E-9) {
				return Math.rint(value * scale) / scale;
			}

			scale *= 10.0;
		}

		return value;
	}

	@Override
	public Object toConfig() {
		return get();
	}

	@Override
	public void fromConfig(Object raw) {
		if (raw instanceof Number number) {
			set(number.doubleValue());
		}
	}

	@Override
	public String displayValue() {
		String text = this.integral ? String.valueOf(intValue())
				: String.format(java.util.Locale.ROOT, "%.2f", get());
		return this.unit.isEmpty() ? text : text + " " + this.unit;
	}
}
