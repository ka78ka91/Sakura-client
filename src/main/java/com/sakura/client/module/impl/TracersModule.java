package com.sakura.client.module.impl;

import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.WorldProjection;
import com.sakura.client.setting.EnumSetting;
import com.sakura.client.setting.NumberSetting;
import com.sakura.client.setting.Tagged;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;

/**
 * Draws a line from a fixed point on the screen to every entity in range.
 *
 * <p>Ported from LiquidBounce's {@code ModuleTracers} (GPL-3.0). The line is a screen space segment, which is
 * what a tracer is by definition; the 3D pipeline is not involved.</p>
 */
public final class TracersModule extends EntityOverlayModule {

	/** Where the lines start. */
	enum Origin implements Tagged {

		CENTER("Center"),
		BOTTOM("Bottom");

		private final String tag;

		Origin(String tag) {
			this.tag = tag;
		}

		@Override
		public String getTag() {
			return this.tag;
		}
	}

	private final EnumSetting<Origin> origin = setting(new EnumSetting<>("Origin",
			"Where the lines start.", Origin.CENTER));
	private final NumberSetting thickness = setting(new NumberSetting("Thickness",
			"Line thickness in GUI pixels.", 1.0, 1.0, 4.0, 1.0, "px"));

	/** Height above the bottom edge the lines start at on {@link Origin#BOTTOM}. */
	private static final float BOTTOM_INSET = 48.0f;

	private final WorldProjection.ScreenPoint point = new WorldProjection.ScreenPoint();

	public TracersModule() {
		super("Tracers", "Draws a line to every entity in range.");
	}

	@Override
	public String getHudSuffix() {
		return isEnabled() ? this.origin.get().getTag() : null;
	}

	@Override
	public void renderOverlay(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		float startX = context.getScaledWindowWidth() * 0.5f;
		float startY = this.origin.get() == Origin.BOTTOM
				? context.getScaledWindowHeight() - BOTTOM_INSET
				: context.getScaledWindowHeight() * 0.5f;
		float lineThickness = (float) this.thickness.get().doubleValue();

		for (Entity entity : collectTargets(client)) {
			Box box = entity.getBoundingBox();
			double centreX = (box.minX + box.maxX) * 0.5;
			double centreY = (box.minY + box.maxY) * 0.5;
			double centreZ = (box.minZ + box.maxZ) * 0.5;

			// Entities behind the camera have no screen position, so they get no line rather than a wrong one.
			if (!WorldProjection.project(centreX, centreY, centreZ, this.point)) {
				continue;
			}

			RenderUtils.drawLine(context, startX, startY, this.point.x, this.point.y, lineThickness,
					colorFor(entity));
		}
	}
}
