package com.sakura.client.module;

import net.minecraft.client.gui.DrawContext;

/**
 * A module that paints into the world overlay.
 *
 * <p>Overlay modules are drawn by {@code WorldOverlayRenderer} once per frame, in the same GUI pass as the
 * HUD, using the world-to-screen mapping in {@code WorldProjection}. They are not HUD elements: they have no
 * position, no anchor and nothing to drag around, they just paint wherever the world is.</p>
 */
public abstract class RenderModule extends Module {

	protected RenderModule(String name, Category category, String description) {
		super(name, category, description);
	}

	/**
	 * Paints this module's overlay for the current frame.
	 *
	 * <p>Only called while the module is enabled and a fresh world-to-screen mapping is available.</p>
	 */
	public void renderOverlay(DrawContext context) {
	}
}
