package com.naruka.client.module;

/**
 * Sidebar groups for feature modules. The order here is the order rendered in the ClickGUI sidebar.
 */
public enum Category {

	COMBAT("Combat"),
	MOVEMENT("Movement"),
	PLAYER("Player"),
	VISUALS("Visuals"),
	HUD("HUD"),
	WORLD("World"),
	MISC("Misc");

	private final String displayName;

	Category(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return this.displayName;
	}
}
