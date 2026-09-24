package com.sakura.client.module;

import com.sakura.client.setting.EnumSetting;
import com.sakura.client.setting.Risk;
import com.sakura.client.setting.Tagged;
import com.sakura.client.setting.Setting;
import com.sakura.client.setting.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A toggleable client feature.
 *
 * <p>Subclasses only implement behaviour hooks; enable/disable bookkeeping, the optional key bind, the
 * settings list and the persisted name used as the config key all live here. {@link #getHudSuffix()} feeds the
 * module list HUD, which renders a module as {@code Name Mode} exactly like the prototype.</p>
 */
public abstract class Module {

	/** Sentinel for "no key bound". */
	public static final int UNBOUND = -1;

	/** Notified whenever any module is switched on or off, used to show the toggle toast. */
	private static Consumer<Module> toggleListener = module -> {
	};

	private final String name;
	private final Category category;
	private final String description;
	private final boolean enabledByDefault;
	private final List<Setting<?>> settings = new ArrayList<>();

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

	/** Registers the listener fired on every module toggle. */
	public static void setToggleListener(Consumer<Module> listener) {
		toggleListener = listener == null ? module -> {
		} : listener;
	}
	// ------------------------------------------------------------------- settings

	/**
	 * Declares a setting belonging to this module.
	 *
	 * <p>Called from the subclass constructor; the settings panel renders whatever is registered here, so a
	 * new parameter needs no UI work.</p>
	 */
	protected final <S extends Setting<?>> S setting(S setting) {
		this.settings.add(setting);
		return setting;
	}

	public final List<Setting<?>> getSettings() {
		return Collections.unmodifiableList(this.settings);
	}

	/** Settings the panel should currently show, i.e. those whose visibility condition holds. */
	public final List<Setting<?>> getVisibleSettings() {
		List<Setting<?>> visible = new ArrayList<>();

		for (Setting<?> setting : this.settings) {
			if (setting.isVisible()) {
				visible.add(setting);
			}
		}

		return visible;
	}

	public final boolean hasSettings() {
		return !this.settings.isEmpty();
	}

	/** @return this module's parameter values, ready to be written to the config file */
	public final Map<String, Object> settingsToMap() {
		return Settings.toMap(this.settings);
	}

	/** Applies parameter values read from the config file. */
	public final void applySettings(Map<String, Object> raw) {
		Settings.applyMap(this.settings, raw);
	}

	/** Resets every parameter of this module to its default. */
	public final void restoreSettings() {
		Settings.restoreAll(this.settings);
	}

	public final void resetSettings() {
		restoreSettings();
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

		// Fired after the hooks so a listener can report a post-toggle mode (e.g. Velocity's active mode).
		toggleListener.accept(this);
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
	/**
	 * The risk the module currently carries, taken from the values its settings are set to.
	 *
	 * <p>A module is only as safe as the mode it is running: Criticals on Jump spoofs nothing, while the same
	 * module on Timer rewrites the client clock. Reading the active values means the label follows the settings
	 * instead of describing the module in general.</p>
	 *
	 * <p>Only settings that are visible right now are counted. A setting the current mode has hidden is not
	 * running, so its risk is not the module's risk: Criticals on Jump must not be labelled outdated just because
	 * the Packet Mode it is not using defaults to NoCheatPlus.</p>
	 *
	 * @return the worst risk among the module's active settings
	 */
	public Risk getRisk() {
		Risk worst = Risk.SAFE;

		for (Setting<?> candidate : getVisibleSettings()) {
			if (candidate instanceof EnumSetting<?> enumSetting && enumSetting.get() instanceof Tagged tagged) {
				Risk risk = tagged.getRisk();

				if (risk.ordinal() > worst.ordinal()) {
					worst = risk;
				}
			}
		}

		return worst;
	}

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
