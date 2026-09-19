package com.sakura.client.module.impl;

import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.WorldProjection;
import com.sakura.client.setting.BooleanSetting;
import com.sakura.client.setting.EnumSetting;
import com.sakura.client.setting.NumberSetting;
import com.sakura.client.setting.Tagged;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;

/**
 * Draws a box around every entity in range.
 *
 * <p>Ported from LiquidBounce's {@code ModuleESP} (GPL-3.0), reduced to the part that can be drawn honestly
 * with the GUI API: a screen space box around the projection of the entity's bounding box.</p>
 *
 * <p>Two deliberate differences from LiquidBounce:</p>
 * <ul>
 *     <li>LiquidBounce draws its box in world space through the 3D pipeline, so it is depth tested and can
 *     fade with distance. This draws in the GUI pass, which means the box is always on top: an entity behind
 *     a wall is boxed exactly like one in the open. That is the honest description of a 2D ESP, and it is why
 *     there is no "through walls" switch — it would not do anything.</li>
 *     <li>An entity straddling the camera plane cannot be drawn at all, because a perspective projection of a
 *     box that surrounds the eye has no screen rectangle. Such a box is skipped rather than drawn wrongly.</li>
 * </ul>
 */
public final class EspModule extends EntityOverlayModule {

	/** How the outline is assembled. */
	enum Style implements Tagged {

		BOX("Box"),
		CORNERS("Corners");

		private final String tag;

		Style(String tag) {
			this.tag = tag;
		}

		@Override
		public String getTag() {
			return this.tag;
		}
	}

	private final EnumSetting<Style> style = setting(new EnumSetting<>("Style",
			"How the box outline is drawn.", Style.BOX));
	private final NumberSetting thickness = setting(new NumberSetting("Thickness",
			"Line thickness in GUI pixels.", 1.0, 1.0, 4.0, 1.0, "px"));
	private final NumberSetting cornerLength = setting(new NumberSetting("Corner Length",
			"Length of each bracket, only used by the Corners style.", 6.0, 2.0, 24.0, 1.0, "px"));
	private final BooleanSetting fill = setting(new BooleanSetting("Fill",
			"Shade the inside of the box as well.", false));

	private final WorldProjection.ScreenRect rect = new WorldProjection.ScreenRect();
	/** Alpha of the shading, applied to whatever colour the entity's group resolved to. */
	private static final int FILL_ALPHA = 0x33;

	public EspModule() {
		super("ESP", "Draws a box around entities in range.");
		this.cornerLength.visibleWhen(() -> this.style.get() == Style.CORNERS);
	}

	@Override
	public String getHudSuffix() {
		return isEnabled() ? this.style.get().getTag() : null;
	}

	@Override
	public void renderOverlay(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		float lineThickness = (float) this.thickness.get().doubleValue();

		for (Entity entity : collectTargets(client)) {
			if (!WorldProjection.projectBox(entity.getBoundingBox(), this.rect)) {
				continue;
			}

			int color = colorFor(entity);

			if (this.fill.get()) {
				RenderUtils.drawRect(context, this.rect.minX, this.rect.minY, this.rect.width(), this.rect.height(),
						(color & 0x00FFFFFF) | (FILL_ALPHA << 24));
			}

			if (this.style.get() == Style.CORNERS) {
				RenderUtils.drawCornerBox(context, this.rect.minX, this.rect.minY, this.rect.maxX, this.rect.maxY,
						(float) this.cornerLength.get().doubleValue(), lineThickness, color);
			} else {
				RenderUtils.drawOutline(context, this.rect.minX, this.rect.minY, this.rect.maxX, this.rect.maxY,
						lineThickness, color);
			}
		}
	}
}
