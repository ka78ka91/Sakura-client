package com.sakura.client.setting;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A min/max pair with one value drawn from it at use time.
 *
 * <p>This is the core randomisation primitive: anticheats profile constant timing and constant speed, so a
 * parameter such as an autoclicker delay or a rotation speed is configured as a range rather than a number.
 * Mirrors LiquidBounce's {@code floatRange}/{@code intRange} values (GPL-3.0).</p>
 */
public class RangeSetting extends Setting<RangeSetting.RangeValue> {

	/** An inclusive interval; constructed so {@code min <= max} whichever way the pair is given. */
	public record RangeValue(double min, double max) {

		public RangeValue {
			if (min > max) {
				double swap = min;
				min = max;
				max = swap;
			}
		}

		public boolean isSingleValue() {
			return min == max;
		}
	}

	private final double lowerBound;
	private final double upperBound;
	private final boolean integral;
	private final String unit;

	public RangeSetting(String name, String description, double defaultMin, double defaultMax,
						double lowerBound, double upperBound, boolean integral, String unit) {
		super(name, description, new RangeValue(defaultMin, defaultMax));
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
		this.integral = integral;
		this.unit = unit == null ? "" : unit;
	}

	public RangeSetting(String name, double defaultMin, double defaultMax,
						double lowerBound, double upperBound, boolean integral, String unit) {
		this(name, "", defaultMin, defaultMax, lowerBound, upperBound, integral, unit);
	}

	@Override
	public SettingType type() {
		return SettingType.RANGE;
	}

	public double getLowerBound() {
		return this.lowerBound;
	}

	public double getUpperBound() {
		return this.upperBound;
	}

	public boolean isIntegral() {
		return this.integral;
	}

	public String getUnit() {
		return this.unit;
	}

	public double getLower() {
		return get().min();
	}

	public double getUpper() {
		return get().max();
	}

	public void setLower(double value) {
		set(new RangeValue(clamp(value), getUpper()));
	}

	public void setUpper(double value) {
		set(new RangeValue(getLower(), clamp(value)));
	}

	/** Draws a value from the configured range; the whole point of this setting type. */
	public double random() {
		double min = getLower();
		double max = getUpper();

		if (min == max) {
			return min;
		}

		double drawn = ThreadLocalRandom.current().nextDouble(min, max);

		return this.integral ? Math.rint(drawn) : drawn;
	}

	/** @return a drawn value rounded to an {@code int} */
	public int randomInt() {
		return (int) Math.round(random());
	}

	@Override
	public void set(RangeValue newValue) {
		if (newValue == null) {
			return;
		}

		super.set(new RangeValue(clamp(newValue.min()), clamp(newValue.max())));
	}

	@Override
	public Object toConfig() {
		return List.of(getLower(), getUpper());
	}

	@Override
	public void fromConfig(Object raw) {
		if (raw instanceof List<?> list && list.size() >= 2
				&& list.get(0) instanceof Number low && list.get(1) instanceof Number high) {
			set(new RangeValue(low.doubleValue(), high.doubleValue()));
		}
	}

	private double clamp(double raw) {
		double clamped = Math.clamp(raw, this.lowerBound, this.upperBound);
		return this.integral ? Math.rint(clamped) : clamped;
	}

	@Override
	public String displayValue() {
		String text = format(getLower()) + " - " + format(getUpper());
		return this.unit.isEmpty() ? text : text + " " + this.unit;
	}

	private String format(double number) {
		return this.integral ? String.valueOf((int) Math.round(number))
				: String.format(Locale.ROOT, "%.2f", number);
	}
}
