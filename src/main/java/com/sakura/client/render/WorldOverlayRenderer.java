package com.sakura.client.render;

import com.sakura.client.SakuraClient;
import com.sakura.client.module.Module;
import com.sakura.client.module.ModuleManager;
import com.sakura.client.module.RenderModule;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

/**
 * Entry point for everything that is drawn over the world: ESP boxes, name tags and tracers.
 *
 * <p>Unlike the HUD, these have no position of their own, so they are not {@code HudModule}s; they are
 * ordinary modules that know how to paint themselves. This class only owns the frame: make sure a
 * world-to-screen mapping exists, then let every enabled {@link RenderModule} draw.</p>
 *
 * <p>The element is added first among the HUD layers, so overlays end up behind the crosshair, the hotbar and
 * the client's own HUD elements instead of covering them. Being a HUD element also means the vanilla rules
 * apply: nothing is drawn while a screen is open, and the F1 "hide HUD" key hides overlays too.</p>
 */
public final class WorldOverlayRenderer {

	private WorldOverlayRenderer() {
	}

	/** Hooks the overlay into the client. Call once from the mod initialiser. */
	public static void register() {
		HudElementRegistry.addFirst(Identifier.of(SakuraClient.MOD_ID, "world_overlay"),
				WorldOverlayRenderer::render);
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();

		if (client.player == null || client.world == null) {
			return;
		}

		WorldProjection.beginFrame(context.getScaledWindowWidth(), context.getScaledWindowHeight());

		// No world frame was captured yet (still loading, or the world is not being drawn), so there is no
		// trustworthy mapping to place anything with.
		if (!WorldProjection.isReady()) {
			return;
		}

		for (Module module : ModuleManager.getAll()) {
			if (module instanceof RenderModule overlay && overlay.isEnabled()) {
				overlay.renderOverlay(context);
			}
		}
	}
}
