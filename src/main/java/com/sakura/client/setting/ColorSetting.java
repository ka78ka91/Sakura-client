package com.sakura.client.setting;

import java.util.Locale;

/**
 * A packed ARGB colour, matching Minecraft's {@code 0xAARRGGBB} convention so it can be handed straight to
 * the render helpers.
 */
public class ColorSetting extends Setting<Integer> {

	public ColorSetting(String name, String description, int defaultArgb) {
		super(name, description, defaultArgb);
	}

	public ColorSetting(String name, int defaultArgb) {
		this(name, "", defaultArgb);
	}

	@Override
	public SettingType type() {
		return SettingType.COLOR;
	}

	public int getAlpha() {
		return (get() >>> 24) & 0xFF;
	}

	public boolean isFullyTransparent() {
		return getAlpha() == 0;
	}

	@Override
	public Object toConfig() {
		return get();
	}

	@Override
	public void fromConfig(Object raw) {
		if (raw instanceof Number number) {
			set(number.intValue());
		}
	}

	/** @return {@code #RRGGBB}, the form the prototype's colour box displays */
	public String getHex() {
		return String.format(Locale.ROOT, "#%06X", get() & 0xFFFFFF);
	}

	@Override
	public String displayValue() {
		return getHex() + " " + (getAlpha() * 100 / 255) + "%";
	}
}
