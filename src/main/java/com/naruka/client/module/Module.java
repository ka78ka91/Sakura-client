package com.naruka.client.module;

/**
 * A toggleable client feature.
 *
 * <p>Subclasses only implement behaviour hooks; enable/disable bookkeeping, the optional key bind and
 * the persisted name used as the config key all live here. {@link #getHudSuffix()} feeds the module
 * list HUD, which renders a module as {@code Name Mode} exactly like the prototype.</p>
 */
public abstract class Module {

	/** Sentinel for "no key bound". */
	public static final int UNBOUND = -1;

	private final String name;
	private final Category category;
	private final String description;
	private final boolean enabledByDefault;

	private boolean enabled;
	private int keybind = UNBOUND;

	protected Module(String name, Category category, String description) {
		this(name, category, description, false);
	}

	protected Module(String name, Category category, String description, boolean enabledByDefault) {
		this.name = name;
		this.category = category;
		this.description = description;
		this.enabledByDefault = enabledByDefault;
		this.enabled = enabledByDefault;
	}

	public final String getName() {
		return this.name;
	}

	public final Category getCategory() {
		return this.category;
	}

	public String getDescription() {
		return this.description;
	}

	public final boolean isEnabledByDefault() {
		return this.enabledByDefault;
	}

	public final boolean isEnabled() {
		return this.enabled;
	}

	public final void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}

		this.enabled = enabled;

		if (enabled) {
			onEnable();
		} else {
			onDisable();
		}
	}

	public final void toggle() {
		setEnabled(!this.enabled);
	}

	public final int getKeybind() {
		return this.keybind;
	}

	public final void setKeybind(int keyCode) {
		this.keybind = keyCode;
	}

	public final boolean hasKeybind() {
		return this.keybind != UNBOUND;
	}

	/** Optional mode label shown next to the name in the module list HUD. */
	public String getHudSuffix() {
		return null;
	}

	/** Invoked once when the module is switched on. */
	public void onEnable() {
	}

	/** Invoked once when the module is switched off. */
	public void onDisable() {
	}

	/** Invoked every client tick while the module is enabled. */
	public void onTick() {
	}

	@Override
	public String toString() {
		return this.name;
	}
}
