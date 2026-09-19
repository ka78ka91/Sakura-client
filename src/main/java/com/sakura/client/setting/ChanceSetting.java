package com.sakura.client.setting;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A 0-100 percentage that is rolled as a chance, so a module can act "most of the time" instead of always.
 *
 * <p>Used by LiquidBounce the same way ({@code percentageChance}), e.g. to only cancel knockback on a share of
 * hits so the pattern is not perfectly regular.</p>
 */
public class ChanceSetting extends Setting<Double> {

	public ChanceSetting(String name, String description, double defaultPercent) {
		super(name, description, Math.clamp(defaultPercent, 0.0, 100.0));
	}

	public ChanceSetting(String name, double defaultPercent) {
		this(name, "", defaultPercent);
	}

	@Override
	public SettingType type() {
		return SettingType.CHANCE;
	}

	/** @return {@code true} on the configured share of calls */
	public boolean roll() {
		double percent = get();
		return percent >= 100.0 || (percent > 0.0 && ThreadLocalRandom.current().nextDouble(100.0) < percent);
	}

	@Override
	public void set(Double newValue) {
		super.set(newValue == null ? 0.0 : Math.clamp(newValue, 0.0, 100.0));
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
		return String.format(Locale.ROOT, "%.0f%%", get());
	}
}
