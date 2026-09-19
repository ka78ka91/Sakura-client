package com.sakura.client.hud.element;

import com.sakura.client.hud.HudAnchor;
import com.sakura.client.hud.HudModule;
import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.RenderUtils.Align;
import com.sakura.client.setting.BooleanSetting;
import com.sakura.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;

import java.util.Locale;

/**
 * Shows the entity currently under the crosshair: name, health bar and distance.
 *
 * <p>Ported from LiquidBounce's target HUD (GPL-3.0). The target is whatever the crosshair points at, held for
 * a moment afterwards so the panel does not flicker while the aim moves across a hitbox — LiquidBounce instead
 * listens to its KillAura's target, which this client does not have.</p>
 *
 * <p>Nothing is drawn without a target, so the panel only appears while there is something to look at.</p>
 */
public final class TargetHudElement extends HudModule {

	private static final float PANEL_WIDTH = 140.0f;
	private static final float PANEL_HEIGHT = 34.0f;
	private static final float PADDING = 6.0f;
	private static final float BAR_HEIGHT = 5.0f;
	private static final float TEXT_Y = 5.0f;

	private static final int PANEL_BG = 0x66000000;
	private static final int NAME_COLOR = 0xFFFFFFFF;
	private static final int DISTANCE_COLOR = 0xFFB0B0B0;
	private static final int BAR_BG = 0x40FFFFFF;

	private final NumberSetting hold = setting(new NumberSetting("Hold",
			"Ticks the panel keeps showing the last target after it leaves the crosshair.", 40.0, 0.0, 200.0, 5.0, "t"));
	private final BooleanSetting showDistance = setting(new BooleanSetting("Distance",
			"Show how far the target is.", true));

	private LivingEntity target;
	private int ticksSinceSeen;

	public TargetHudElement() {
		super("TargetHUD", "Name and health of the entity under the crosshair",
				HudAnchor.BOTTOM_CENTER, 0.0f, 60.0f, false);
	}

	@Override
	public void onTick() {
		LivingEntity found = targetOf(MinecraftClient.getInstance());

		if (found != null) {
			this.target = found;
			this.ticksSinceSeen = 0;
			return;
		}

		if (this.target == null) {
			return;
		}

		this.ticksSinceSeen++;

		if (this.ticksSinceSeen > this.hold.get().intValue()
				|| this.target.isRemoved() || !this.target.isAlive()) {
			this.target = null;
		}
	}

	/**
	 * @return the living entity under the crosshair, or {@code null} when the crosshair is not on one. The
	 * client's own player is never a target.
	 */
	public static LivingEntity targetOf(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			return null;
		}

		if (client.crosshairTarget instanceof EntityHitResult hit
				&& hit.getEntity() instanceof LivingEntity living
				&& living != client.player) {
			return living;
		}

		return null;
	}

	/** @return current health as a fraction of the maximum, clamped to 0..1 */
	public static float healthFraction(LivingEntity entity) {
		float maximum = Math.max(1.0f, entity.getMaxHealth());

		return Math.min(1.0f, Math.max(0.0f, entity.getHealth() / maximum));
	}

	/** @return health colour, red at death's door and green at full health */
	public static int healthColor(float fraction) {
		int red = Math.round((1.0f - fraction) * 255.0f);
		int green = Math.round(fraction * 255.0f);

		return 0xFF000000 | (red << 16) | (green << 8);
	}

	@Override
	public float getWidth() {
		return PANEL_WIDTH;
	}

	@Override
	public float getHeight() {
		return PANEL_HEIGHT;
	}

	/** The entity this element would draw right now, or {@code null}. */
	public LivingEntity getTarget() {
		return this.target;
	}

	@Override
	public void render(DrawContext context, float x, float y) {
		MinecraftClient client = MinecraftClient.getInstance();
		LivingEntity current = this.target;

		if (current == null || client.player == null) {
			return;
		}

		RenderUtils.drawRoundedRect(context, x, y, PANEL_WIDTH, PANEL_HEIGHT, cornerRadius(), PANEL_BG);

		String name = current.getDisplayName().getString();
		float right = x + PANEL_WIDTH - PADDING;

		RenderUtils.drawTextVCentered(context, String.format(Locale.ROOT, "%.1f", current.getHealth()),
				right, y + TEXT_Y, 10.0f, healthColor(healthFraction(current)), true, Align.RIGHT);

		float nameX = x + PADDING;

		if (this.showDistance.get()) {
			float healthWidth = RenderUtils.textWidth(String.format(Locale.ROOT, "%.1f", current.getHealth()));
			float available = PANEL_WIDTH - PADDING * 2.0f - healthWidth - 6.0f;
			String distance = String.format(Locale.ROOT, "%.1fm", distanceTo(client, current));
			// Long names (or a long team prefix) would run into the health number, so they are cut off instead.
			float room = available - RenderUtils.textWidth(distance) - 4.0f;
			String shown = RenderUtils.textWidth(name) <= room ? name : RenderUtils.trimToWidth(name, room);

			RenderUtils.drawTextVCentered(context, shown, nameX, y + TEXT_Y, 10.0f, NAME_COLOR, true, Align.LEFT);
			RenderUtils.drawTextVCentered(context, distance, right, y + TEXT_Y, 10.0f, DISTANCE_COLOR, true,
					Align.RIGHT);
		} else {
			float healthWidth = RenderUtils.textWidth(String.format(Locale.ROOT, "%.1f", current.getHealth()));
			float room = PANEL_WIDTH - PADDING * 2.0f - healthWidth - 6.0f;

			RenderUtils.drawTextVCentered(context, RenderUtils.trimToWidth(name, room), nameX, y + TEXT_Y, 10.0f,
					NAME_COLOR, true, Align.LEFT);
		}

		float fraction = healthFraction(current);
		float barY = y + PANEL_HEIGHT - PADDING - BAR_HEIGHT + 1.0f;
		float barWidth = PANEL_WIDTH - PADDING * 2.0f;

		RenderUtils.drawRect(context, x + PADDING, barY, barWidth, BAR_HEIGHT, BAR_BG);
		RenderUtils.drawRect(context, x + PADDING, barY, barWidth * fraction, BAR_HEIGHT, healthColor(fraction));
	}

	private static double distanceTo(MinecraftClient client, LivingEntity entity) {
		Box box = entity.getBoundingBox();

		return Math.sqrt(client.player.squaredDistanceTo(
				(box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5));
	}
}
