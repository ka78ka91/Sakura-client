package com.sakura.client.hud;

import com.sakura.client.config.ConfigManager;
import com.sakura.client.config.SakuraConfig;
import com.sakura.client.module.ModuleManager;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owns every {@link HudModule}: registration, per-frame rendering and position persistence.
 *
 * <p>Elements are also registered with {@link ModuleManager}, so the ClickGUI can toggle them like any
 * other module and the config layer persists their state for free.</p>
 */
public final class HudManager {

	private static final List<HudModule> ELEMENTS = new ArrayList<>();

	private HudManager() {
	}

	public static void register(HudModule element) {
		ELEMENTS.add(element);
		ModuleManager.register(element);
	}

	public static List<HudModule> getElements() {
		return Collections.unmodifiableList(ELEMENTS);
	}

	/** Paints every enabled element. */
	public static void render(DrawContext context) {
		int screenWidth = context.getScaledWindowWidth();
		int screenHeight = context.getScaledWindowHeight();

		for (HudModule element : ELEMENTS) {
			if (element.isEnabled()) {
				element.render(context, element.resolveX(screenWidth), element.resolveY(screenHeight));
			}
		}
	}

	/** Paints every element regardless of state, dimming the disabled ones. Used by the HUD editor. */
	public static void renderEditorPreview(DrawContext context) {
		int screenWidth = context.getScaledWindowWidth();
		int screenHeight = context.getScaledWindowHeight();

		for (HudModule element : ELEMENTS) {
			element.render(context, element.resolveX(screenWidth), element.resolveY(screenHeight));
		}
	}

	/**
	 * Topmost element under the cursor, or {@code null}. Disabled elements are included so the HUD
	 * editor can position an element before switching it on.
	 *
	 * <p>Elements are registered in the order they are painted, so a later one overlaps an earlier one.
	 * Iterating forwards and keeping the last hit therefore returns the element that is visually on top,
	 * which is the one the player is pointing at.</p>
	 */
	public static HudModule getAt(double mouseX, double mouseY, int screenWidth, int screenHeight) {
		HudModule found = null;

		for (HudModule element : ELEMENTS) {
			if (element.isHovered(mouseX, mouseY, screenWidth, screenHeight)) {
				found = element;
			}
		}

		return found;
	}

	// --------------------------------------------------------------- persistence

	public static void loadPositions() {
		SakuraConfig config = ConfigManager.get();

		for (HudModule element : ELEMENTS) {
			SakuraConfig.HudPosition position = config.hudPositions.get(element.getName());

			if (position != null) {
				element.setPosition(position.x, position.y);
			} else {
				element.clearPosition();
			}
		}
	}

	public static void savePositions() {
		SakuraConfig config = ConfigManager.get();
		config.hudPositions.clear();

		for (HudModule element : ELEMENTS) {
			if (element.isPositioned()) {
				config.hudPositions.put(element.getName(),
						new SakuraConfig.HudPosition(element.getExplicitX(), element.getExplicitY()));
			}
		}

		ConfigManager.save();
	}

	/** Clears every saved position and persists the default layout. */
	public static void resetPositions() {
		for (HudModule element : ELEMENTS) {
			element.clearPosition();
		}

		savePositions();
	}
}
