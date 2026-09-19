package com.naruka.client.setting;

/** A plain on/off parameter. */
public class BooleanSetting extends Setting<Boolean> {

	public BooleanSetting(String name, String description, boolean defaultValue) {
		super(name, description, defaultValue);
	}

	public BooleanSetting(String name, boolean defaultValue) {
		this(name, "", defaultValue);
	}

	@Override
	public SettingType type() {
		return SettingType.BOOLEAN;
	}

	/** Flips the value and returns the new state. */
	public boolean toggle() {
		set(!get());
		return get();
	}

	@Override
	public Object toConfig() {
		return get();
	}

	@Override
	public void fromConfig(Object raw) {
		if (raw instanceof Boolean bool) {
			set(bool);
		}
	}

	@Override
	public String displayValue() {
		return get() ? "on" : "off";
	}
}
