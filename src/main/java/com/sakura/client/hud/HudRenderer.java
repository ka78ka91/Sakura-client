package com.sakura.client.hud;

import com.sakura.client.SakuraClient;
import com.sakura.client.notification.NotificationManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

/**
 * HUD entry point.
 *
 * <p>Registers a single Fabric HUD element that delegates to {@link HudManager}. In 1.21.11 both
 * {@code HudElementRegistry} and the older {@code HudRenderCallback} exist; only the registry form is
 * non-deprecated, and the element contract is {@code render(DrawContext, RenderTickCounter)} either
 * way.</p>
 */
public final class HudRenderer {

	private HudRenderer() {
	}

	/** Hooks the HUD into the client. Call once from the mod initialiser. */
	public static void register() {
		HudElementRegistry.addLast(Identifier.of(SakuraClient.MOD_ID, "hud"), HudRenderer::onHudRender);
	}

	private static void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();

		if (client.player == null || client.options == null || client.options.hudHidden) {
			return;
		}

		HudManager.render(context);

		// Notices are drawn here while no screen is open, and by the menu when one is —otherwise a toggle made
		// in the menu would be confirmed twice, in two different corners.
		if (client.currentScreen == null && !NotificationManager.isEmpty()) {
			NotificationManager.render(context, context.getScaledWindowWidth() - 6.0f, 6.0f, 1.0f);
		}
	}
}
