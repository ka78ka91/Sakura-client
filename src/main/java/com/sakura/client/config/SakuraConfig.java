package com.sakura.client.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Plain data model for {@code config/sakura.json}.
 *
 * <p>Fields are public and initialised inline so Gson can round-trip them without a custom adapter:
 * the implicit no-arg constructor runs those initialisers, which keeps every collection non-null even
 * when the file on disk is missing keys.</p>
 */
public class SakuraConfig {

	// ---------------------------------------------------------------- appearance
	/**
	 * On-disk format version, bumped whenever a migration needs to tell old files from new ones.
	 * Written into every save and ignored on read for now; future format changes branch on it.
	 */
	public int schemaVersion = 1;
	/** UI accent, packed ARGB. Defaults to the prototype's purple. */
	public int accentColor = 0xFFA06EFF;
	public boolean descriptions = true;
	public float hudCornerRadius = 16.0f;
	/**
	 * Upper bound on the menu's scale, where 1.0 renders the window at its designed 900x550. The value
	 * actually applied is additionally capped so the window always fits the current screen; see
	 * {@code ClickGuiScreen.layout()}. The prototype showed a sample value of 85% here, but it also
	 * specified a 900x550 window, so the default is 100% and the two no longer contradict each other.
	 */
	public float guiScale = 1.0f;
	public String moduleSettingsPanel = "Side panel";
	/** Sidebar page the menu reopens on: a {@code Category} or {@code ClickGuiScreen.UtilityPage} enum name. */
	public String lastGuiPage = "Settings";

	// -------------------------------------------------------------------- modules
	public Map<String, Boolean> moduleStates = new HashMap<>();
	public Map<String, Integer> moduleKeybinds = new HashMap<>();

	/**
	 * Per-module parameter values, keyed by module name and then by setting name.
	 *
	 * <p>Values are kept untyped ({@code Boolean}, {@code Double}, {@code String}, {@code List}) and handed to
	 * the owning {@code Setting} to interpret, so a malformed or outdated entry is rejected in one place
	 * instead of breaking the whole file.</p>
	 */
	public Map<String, Map<String, Object>> moduleSettings = new HashMap<>();

	// ------------------------------------------------------------------------ hud
	public Map<String, HudPosition> hudPositions = new HashMap<>();

	/** Saved top-left corner of a HUD element, in GUI-scaled pixels. */
	public static class HudPosition {
		public float x;
		public float y;

		public HudPosition() {
		}

		public HudPosition(float x, float y) {
			this.x = x;
			this.y = y;
		}
	}

	/**
	 * Copies every field from {@code other} into this instance.
	 *
	 * <p>ConfigManager keeps one live config object for the whole session and parses files into a temporary
	 * instance first; copying fields in place means a holder of the live object never ends up mutating a
	 * stale copy the persistence layer no longer writes out.</p>
	 */
	void copyFrom(SakuraConfig other) {
		this.schemaVersion = other.schemaVersion;
		this.accentColor = other.accentColor;
		this.descriptions = other.descriptions;
		this.hudCornerRadius = other.hudCornerRadius;
		this.guiScale = other.guiScale;
		this.moduleSettingsPanel = other.moduleSettingsPanel;
		this.lastGuiPage = other.lastGuiPage;
		this.moduleStates.clear();
		this.moduleStates.putAll(other.moduleStates);
		this.moduleKeybinds.clear();
		this.moduleKeybinds.putAll(other.moduleKeybinds);
		this.moduleSettings.clear();
		this.moduleSettings.putAll(other.moduleSettings);
		this.hudPositions.clear();
		this.hudPositions.putAll(other.hudPositions);
	}
}
