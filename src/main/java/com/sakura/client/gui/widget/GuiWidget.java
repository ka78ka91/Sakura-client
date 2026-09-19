package com.sakura.client.gui.widget;

import net.minecraft.client.gui.Click;

/**
 * Minimal contract every ClickGUI widget implements, so the screen can dispatch input without knowing
 * concrete widget types.
 *
 * <p>Widgets record their own hit box while drawing, which is safe because the render pass always runs
 * before the input pass for a given frame.</p>
 */
public interface GuiWidget {

	/** @return {@code true} when the click was consumed */
	boolean mouseClicked(Click click);

	default boolean mouseDragged(Click click, double offsetX, double offsetY) {
		return false;
	}

	default boolean mouseReleased(Click click) {
		return false;
	}

	/** Default no-op so non-focusable widgets need not implement it. */
	default boolean keyPressed(net.minecraft.client.input.KeyInput input) {
		return false;
	}
}
