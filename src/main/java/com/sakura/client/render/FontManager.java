package com.sakura.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;

/**
 * The one place all client text rendering asks for its renderer.
 *
 * <p>Today it hands out the vanilla {@code textRenderer}, which is also the safety net: every custom-font
 * idea funnels through {@link #font()}, so a failed font load degrades to the exact rendering the client
 * shipped with instead of leaving callers without a renderer. Loading a TTF from the fonts folder needs a
 * runtime font registration path that the 1.21.11 API does not expose cleanly; until that is settled with a
 * verified approach, the vanilla path is the only one — the {@code customFont} config flag already exists
 * but stays inert.</p>
 */
public final class FontManager {

	private FontManager() {
	}

	/** @return the renderer every {@code RenderUtils} text draw goes through */
	public static TextRenderer font() {
		return MinecraftClient.getInstance().textRenderer;
	}
}
