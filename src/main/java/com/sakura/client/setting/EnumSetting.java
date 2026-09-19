package com.sakura.client.setting;

import java.util.List;

/**
 * One of a fixed set of named modes.
 *
 * <p>Constants are persisted by {@link Enum#name()} rather than by ordinal, so reordering the enum in a later
 * version cannot silently turn one mode into another. Modes implementing {@link Tagged} supply their own
 * display label and {@link Risk} rating.</p>
 *
 * @param <E> the mode enum
 */
public class EnumSetting<E extends Enum<E>> extends Setting<E> {

	private final List<E> values;

	public EnumSetting(String name, String description, E defaultValue) {
		super(name, description, defaultValue);
		this.values = List.of(defaultValue.getDeclaringClass().getEnumConstants());
	}

	public EnumSetting(String name, E defaultValue) {
		this(name, "", defaultValue);
	}

	@Override
	public SettingType type() {
		return SettingType.ENUM;
	}

	public List<E> getValues() {
		return this.values;
	}

	/** Display label of {@code value}, using {@link Tagged#getTag()} when implemented. */
	public String labelOf(E value) {
		return value instanceof Tagged tagged ? tagged.getTag() : value.name();
	}

	/** Risk label of {@code value}, {@link Risk#SAFE} unless {@link Tagged} says otherwise. */
	public Risk riskOf(E value) {
		return value instanceof Tagged tagged ? tagged.getRisk() : Risk.SAFE;
	}

	public Risk getRisk() {
		return riskOf(get());
	}

	public boolean is(E value) {
		return get() == value;
	}

	/** Advances to the next mode, wrapping around; used by the panel and by key binds. */
	public void cycle() {
		int next = (this.values.indexOf(get()) + 1) % this.values.size();
		set(this.values.get(next));
	}

	@Override
	public Object toConfig() {
		return get().name();
	}

	@Override
	public void fromConfig(Object raw) {
		if (!(raw instanceof String name)) {
			return;
		}

		for (E candidate : this.values) {
			if (candidate.name().equalsIgnoreCase(name)) {
				set(candidate);
				return;
			}
		}
	}

	@Override
	public String displayValue() {
		return labelOf(get());
	}
}
