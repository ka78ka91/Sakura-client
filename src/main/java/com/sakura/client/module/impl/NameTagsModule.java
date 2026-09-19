package com.sakura.client.module.impl;

import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.WorldProjection;
import com.sakura.client.setting.BooleanSetting;
import com.sakura.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;

import java.util.Locale;

/**
 * Draws the name of every entity in range above its head.
 *
 * <p>Ported from LiquidBounce's {@code ModuleNameTags} (GPL-3.0), reduced to the text part: name, optional
 * health and optional distance, on an optional panel. LiquidBounce also renders its nametags through the 3D
 * pipeline so they can be depth tested and use the entity's own font; this draws in the GUI pass, which means
 * a tag is always visible, also through walls.</p>
 *
 * <p>The text is scaled through the GUI matrix stack rather than by picking a bigger font, the same way the
 * ClickGUI draws its section titles, so the scale setting is exact and costs nothing per frame.</p>
 */
public final class NameTagsModule extends EntityOverlayModule {

	private final NumberSetting scale = setting(new NumberSetting("Scale",
			"Text scale, 1.0 is the normal font size.", 1.0, 0.5, 2.0, 0.1, "x"));
	private final BooleanSetting showHealth = setting(new BooleanSetting("Show Health",
			"Append the entity's current health.", true));
	private final BooleanSetting showDistance = setting(new BooleanSetting("Show Distance",
			"Append the distance to the entity.", false));
	private final BooleanSetting background = setting(new BooleanSetting("Background",
			"Draw a dark panel behind the text.", false));

	/** Panel colour, deliberately translucent so the world stays readable behind it. */
	private static final int BACKGROUND_COLOR = 0x80000000;
	private static final int DISTANCE_COLOR = 0xFFB0B0B0;
	private static final double HEAD_OFFSET = 0.4;

	private final WorldProjection.ScreenPoint point = new WorldProjection.ScreenPoint();

	public NameTagsModule() {
		super("NameTags", "Draws entity names above their heads.");
	}

	@Override
	public String getHudSuffix() {
		return isEnabled() ? String.format(Locale.ROOT, "%.1fx", this.scale.get()) : null;
	}

	@Override
	public void renderOverlay(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		float textScale = (float) this.scale.get().doubleValue();

		for (Entity entity : collectTargets(client)) {
			Box box = entity.getBoundingBox();
			double centreX = (box.minX + box.maxX) * 0.5;
			double centreZ = (box.minZ + box.maxZ) * 0.5;

			if (!WorldProjection.project(centreX, box.maxY + HEAD_OFFSET, centreZ, this.point)) {
				continue;
			}

			String name = entity.getDisplayName().getString();
			String health = this.showHealth.get() && entity instanceof LivingEntity living
					? String.format(Locale.ROOT, " %.1f", living.getHealth()) : null;
			String distance = this.showDistance.get()
					? String.format(Locale.ROOT, " %.1fm", client.player.distanceTo(entity)) : null;

			int nameWidth = RenderUtils.textWidth(name);
			int healthWidth = health == null ? 0 : RenderUtils.textWidth(health);
			int distanceWidth = distance == null ? 0 : RenderUtils.textWidth(distance);
			int totalWidth = nameWidth + healthWidth + distanceWidth;
			int lineHeight = RenderUtils.fontHeight();

			// Centred over the entity, with the bottom of the line sitting just above its head.
			float anchorX = this.point.x - totalWidth * textScale * 0.5f;
			float anchorY = this.point.y - lineHeight * textScale;

			context.getMatrices().pushMatrix();
			context.getMatrices().translate(anchorX, anchorY);
			context.getMatrices().scale(textScale, textScale);

			if (this.background.get()) {
				RenderUtils.drawRect(context, -2.0f, -1.0f, totalWidth + 4.0f, lineHeight + 2.0f, BACKGROUND_COLOR);
			}

			float cursor = 0.0f;
			RenderUtils.drawText(context, name, cursor, 0.0f, colorFor(entity), true);
			cursor += nameWidth;

			if (health != null) {
				RenderUtils.drawText(context, health, cursor, 0.0f, healthColor((LivingEntity) entity), true);
				cursor += healthWidth;
			}

			if (distance != null) {
				RenderUtils.drawText(context, distance, cursor, 0.0f, DISTANCE_COLOR, true);
			}

			context.getMatrices().popMatrix();
		}
	}

	/**
	 * Health colour, fading from red at death's door to green at full health.
	 *
	 * <p>The health bar numeric is what a player actually looks at in a fight, so it is coloured instead of
	 * using the group colour of the entity.</p>
	 */
	private static int healthColor(LivingEntity entity) {
		float maximum = Math.max(1.0f, entity.getMaxHealth());
		float ratio = Math.min(1.0f, Math.max(0.0f, entity.getHealth() / maximum));
		int red = Math.round((1.0f - ratio) * 255.0f);
		int green = Math.round(ratio * 255.0f);

		return 0xFF000000 | (red << 16) | (green << 8);
	}
}
