package com.sakura.client.setting;

import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/**
 * Base class for every configurable module parameter.
 *
 * <p>Design follows the value model used by LiquidBounce ({@code config/types/Value.kt}, GPL-3.0): a setting
 * carries a name, a default, an optional visibility condition, and serialises itself. That is what lets the
 * settings panel render any module without per-module UI code.</p>
 *
 * @param <T> the value type held by this setting
 */
public abstract class Setting<T> {

	/**
	 * Invoked after any setting anywhere changed. The GUI uses it to mark itself dirty and persist once on
	 * close, instead of writing the config file on every slider frame.
	 */
	private static Consumer<Setting<?>> globalChangeListener = setting -> {
	};

	private final String name;
	private final String description;
	private final T defaultValue;

	protected T value;

	private BooleanSupplier visibleWhen = () -> true;
	private Consumer<T> changedListener;

	protected Setting(String name, String description, T defaultValue) {
		this.name = name;
		this.description = description == null ? "" : description;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	/** Registers the listener fired on every setting change. */
	public static void setGlobalChangeListener(Consumer<Setting<?>> listener) {
		globalChangeListener = listener == null ? setting -> {
		} : listener;
	}

	// ------------------------------------------------------------------ metadata

	/** How the settings panel should render this setting. */
	public abstract SettingType type();

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public T getDefault() {
		return this.defaultValue;
	}

	/** Whether the settings panel should show this setting right now. */
	public boolean isVisible() {
		return this.visibleWhen.getAsBoolean();
	}

	/** Hides this setting unless {@code condition} holds, e.g. a parameter that only applies to one mode. */
	public Setting<T> visibleWhen(BooleanSupplier condition) {
		this.visibleWhen = condition == null ? () -> true : condition;
		return this;
	}

	// --------------------------------------------------------------------- value

	public T get() {
		return this.value;
	}

	public void set(T newValue) {
		if (newValue == null || newValue.equals(this.value)) {
			return;
		}

		this.value = newValue;

		if (this.changedListener != null) {
			this.changedListener.accept(newValue);
		}

		globalChangeListener.accept(this);
	}

	public Setting<T> onChange(Consumer<T> listener) {
		this.changedListener = listener;
		return this;
	}

	/** Restores this single setting to its default. */
	public void restore() {
		set(this.defaultValue);
	}

	public boolean isDefault() {
		return this.defaultValue.equals(this.value);
	}

	// --------------------------------------------------------------- persistence

	/** @return the JSON representation written into {@code moduleSettings} in the config file */
	public abstract Object toConfig();

	/** Applies a value read from the config file, ignoring anything malformed. */
	public abstract void fromConfig(Object raw);

	/** Human readable value for the settings panel header. */
	public String displayValue() {
		return String.valueOf(this.value);
	}
}
