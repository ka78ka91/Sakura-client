package com.naruka.client.hud;

/**
 * Default screen placement for a HUD element. Used until the user drags the element in the HUD
 * editor, after which an explicit position from the config takes over.
 */
public enum HudAnchor {

	TOP_LEFT,
	TOP_CENTER,
	TOP_RIGHT,
	BOTTOM_LEFT,
	BOTTOM_CENTER,
	BOTTOM_RIGHT;

	public float resolveX(int screenWidth, float width, float offset) {
		return switch (this) {
			case TOP_LEFT, BOTTOM_LEFT -> offset;
			case TOP_CENTER, BOTTOM_CENTER -> (screenWidth - width) / 2.0f + offset;
			case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - width - offset;
		};
	}

	public float resolveY(int screenHeight, float height, float offset) {
		return switch (this) {
			case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> offset;
			case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> screenHeight - height - offset;
		};
	}

	public boolean isTop() {
		return this == TOP_LEFT || this == TOP_CENTER || this == TOP_RIGHT;
	}
}
