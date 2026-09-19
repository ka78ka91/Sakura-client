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
}
