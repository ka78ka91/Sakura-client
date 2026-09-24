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

	/**
	 * @return {@code true} when this colour carries no alpha at all
	 *
	 * <p>Several settings — every HUD element's "Base color" among them — use a fully transparent value as the
	 * sentinel for "not picked, follow the theme" rather than as a colour in its own right. Callers that paint
	 * a swatch must check this before drawing the value, and {@link #getDisplayHex()} does the same for the
	 * read-out.</p>
	 */
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

	/**
	 * @return the hex read-out to show in the UI: the colour, or {@code "Theme"} while this setting is at the
	 * fully transparent sentinel, so a "not picked" value never renders as the misleading {@code #000000}
	 */
	public String getDisplayHex() {
		return isFullyTransparent() ? "Theme" : getHex();
	}

	@Override
	public String displayValue() {
		return getDisplayHex() + " " + (getAlpha() * 100 / 255) + "%";
	}
}
