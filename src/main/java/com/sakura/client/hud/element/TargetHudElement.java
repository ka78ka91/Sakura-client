package com.sakura.client.hud.element;


import com.sakura.client.render.Theme;
import com.sakura.client.config.ConfigManager;
import com.sakura.client.hud.HudAnchor;
import com.sakura.client.hud.HudModule;
import com.sakura.client.module.impl.TargetProviders;
import com.sakura.client.render.Animations;
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
 * Shows the entity a combat module is working on — or, when none is, whatever is under the crosshair — with its
 * name, health bar and distance.
 *
 * <p>Ported from LiquidBounce's target HUD (GPL-3.0), which listens to its KillAura's target; this element
 * originally could not, because the client had no module that picked a target of its own, so it read the
 * crosshair instead. It now asks {@link TargetProviders} first and falls back to the crosshair only when no
 * module claims a target, which is what makes the panel useful while an aura is fighting something the player is
 * not looking at.</p>
 *
 * <p>Either way the target is held for a moment after it is lost, so the panel does not flicker while the aim
 * or the aura's selection moves across targets.</p>
 *
 * <p>Everything on the panel moves: the health bar eases toward the real health, quickly when the target is
 * losing health and slowly while it regenerates, with a paler segment left behind over the part that was just
 * lost. A drop in health also flashes the panel red for a moment. The panel itself fades in when a target
 * appears and out once the hold timer has run out, instead of popping in and out, and a name too long for its
 * box scrolls.</p>
 *
 * <p>Nothing is drawn without a target, so the panel only appears while there is something to look at.</p>
 */
public final class TargetHudElement extends HudModule {

	private static final float PANEL_WIDTH = 140.0f;
	private static final float PANEL_HEIGHT = 34.0f;
	private static final float PADDING = 6.0f;
	private static final float BAR_HEIGHT = 5.0f;
	/** Vertical offset of the health bar inside the panel. */
	private static final float BAR_TOP = 22.0f;
	private static final float TEXT_Y = 5.0f;
	private static final float TEXT_BOX = 10.0f;
	/** Reserved width on the right of the bar, where the health number sits. */
	private static final float HEALTH_COLUMN = 32.0f;
	private static final float NAME_GAP = 6.0f;
	/** Losing health is drawn fast, regenerating slowly; the difference is what makes a hit feel like one. */
	private static final float HEALTH_DOWN_SPEED = 15.0f;
	private static final float HEALTH_UP_SPEED = 5.0f;
	/** The paler segment over freshly lost health drains away at this speed. */
	private static final float TRAIL_SPEED = 3.2f;
	private static final float FADE_IN_SPEED = 10.0f;
	private static final float FADE_OUT_SPEED = 7.0f;
	private static final float FLASH_DECAY_SPEED = 3.6f;
	private static final float DAMAGE_THRESHOLD = 0.001f;
	private static final float FADE_EPSILON = 0.01f;
	private static final float MARQUEE_SPEED = 22.0f;
	private static final float MARQUEE_HOLD_MILLIS = 900.0f;
	private static final long ACCENT_PULSE_MILLIS = 2600L;
	private static final int BAR_BG = 0x40FFFFFF;
	private static final int BAR_HIGHLIGHT = 0x1FFFFFFF;
	/** What a bar looks like over health that has just been taken off. */
	private static final int BAR_TRAIL = 0xCCFFFFFF;
	private static final int FLASH_COLOR = 0xFFFF3B48;
	private static final int FLASH_BORDER = 0x66FF5560;
	private static final int HEALTH_GOOD = 0xFF5BE86B;
	private static final int HEALTH_WARN = 0xFFFFC04D;
	private static final int HEALTH_BAD = 0xFFFF4D4D;

	/** Glass body: a dark, slightly cool gradient drawn over the blurred world. */
	private static final float GLASS_SHADOW_SPREAD = 4.0f;

	private final NumberSetting hold = setting(new NumberSetting("Hold",
			"Ticks the panel keeps showing the last target after it leaves the crosshair.", 40.0, 0.0, 200.0, 5.0, "t"));
	private final BooleanSetting showDistance = setting(new BooleanSetting("Distance",
			"Show how far the target is.", true));

	private final Animations.Clock clock = new Animations.Clock();
	private LivingEntity target;
	private int ticksSinceSeen;

	/** True while there is data worth drawing, including during the fade out after the target is gone. */
	private boolean hasContent;
	private float visibility;
	private float shownHealth;
	private float trailHealth;
	private float flash;
	private float lastFraction = -1.0f;
	private float shownMaxHealth = 20.0f;
	private String shownName = "";
	private String shownDistance = "";
	private float marqueeOffset;
	private float marqueeDirection = 1.0f;
	private float marqueeHold = MARQUEE_HOLD_MILLIS;

	public TargetHudElement() {
		super("TargetHUD", "Name and health of the entity under the crosshair",
				HudAnchor.BOTTOM_CENTER, 0.0f, 60.0f, false);
	}

	@Override
	public void onTick() {
		LivingEntity found = currentTarget(MinecraftClient.getInstance());

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
	 * @return the entity to show: the first combat module's target, or the crosshair's when none has one
	 *
	 * <p>The module target wins because it is the one the player is acting on. Falling back rather than
	 * preferring means the panel still works with no combat module switched on at all, which is how it behaved
	 * before the providers existed.</p>
	 */
	public static LivingEntity currentTarget(MinecraftClient client) {
		LivingEntity provided = TargetProviders.current();

		if (provided != null && !provided.isRemoved() && provided.isAlive()) {
			return provided;
		}

		return targetOf(client);
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

	/**
	 * @return health colour, red at death's door and green at full health. The ramp passes through amber rather
	 * than interpolating straight from red to green, which would go through a muddy brown.
	 */
	public static int healthColor(float fraction) {
		float clamped = Animations.clamp01(fraction);

		return clamped < 0.5f
				? RenderUtils.mix(HEALTH_BAD, HEALTH_WARN, clamped * 2.0f)
				: RenderUtils.mix(HEALTH_WARN, HEALTH_GOOD, (clamped - 0.5f) * 2.0f);
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

		if (client.player == null) {
			this.hasContent = false;
			return;
		}

		float delta = this.clock.tick();

		if (current != null) {
			trackTarget(client, current, delta);
		} else if (!this.hasContent) {
			// No target and nothing left over to fade: the panel stays off screen entirely.
			return;
		}

		this.visibility = Animations.approach(this.visibility, current != null ? 1.0f : 0.0f,
				current != null ? FADE_IN_SPEED : FADE_OUT_SPEED, delta);

		if (this.visibility <= FADE_EPSILON) {
			this.hasContent = false;
			return;
		}

		drawPanel(context, x, y);
	}

	/** Eases every animated value toward the target the crosshair is on. */
	private void trackTarget(MinecraftClient client, LivingEntity entity, float delta) {
		float fraction = healthFraction(entity);

		if (!this.hasContent) {
			// A fresh target starts from its real health, so the bar never travels in from the last one.
			this.shownHealth = fraction;
			this.trailHealth = fraction;
			this.flash = 0.0f;
			this.lastFraction = fraction;
			this.visibility = 0.0f;
			this.hasContent = true;
			this.shownName = "";
		}

		if (fraction < this.lastFraction - DAMAGE_THRESHOLD) {
			this.flash = 1.0f;
		}

		this.lastFraction = fraction;
		this.shownHealth = Animations.approach(this.shownHealth, fraction,
				fraction < this.shownHealth ? HEALTH_DOWN_SPEED : HEALTH_UP_SPEED, delta);
		this.flash = Animations.approach(this.flash, 0.0f, FLASH_DECAY_SPEED, delta);
		this.shownMaxHealth = Math.max(1.0f, entity.getMaxHealth());
		this.shownDistance = String.format(Locale.ROOT, "%.1fm", distanceTo(client, entity));

		// The trail only ever sits above the bar: healing pulls it up instantly instead of leaving a gap.
		this.trailHealth = this.shownHealth >= this.trailHealth
				? this.shownHealth
				: Animations.approach(this.trailHealth, this.shownHealth, TRAIL_SPEED, delta);

		String name = entity.getDisplayName().getString();

		if (!name.equals(this.shownName)) {
			this.shownName = name;
			this.marqueeOffset = 0.0f;
			this.marqueeDirection = 1.0f;
			this.marqueeHold = MARQUEE_HOLD_MILLIS;
		}

		advanceMarquee(delta);
	}

	/** Scrolls a name that is wider than its box back and forth, pausing at both ends. */
	private void advanceMarquee(float delta) {
		float overflow = RenderUtils.textWidth(this.shownName) - nameBoxWidth();

		if (overflow <= 0.5f) {
			this.marqueeOffset = 0.0f;

			return;
		}

		if (this.marqueeHold > 0.0f) {
			this.marqueeHold -= delta * 1000.0f;

			return;
		}

		this.marqueeOffset += MARQUEE_SPEED * delta * this.marqueeDirection;

		if (this.marqueeOffset >= overflow) {
			this.marqueeOffset = overflow;
			this.marqueeDirection = -1.0f;
			this.marqueeHold = MARQUEE_HOLD_MILLIS;
		} else if (this.marqueeOffset <= 0.0f) {
			this.marqueeOffset = 0.0f;
			this.marqueeDirection = 1.0f;
			this.marqueeHold = MARQUEE_HOLD_MILLIS;
		}
	}

	/** @return the width the name may use before it runs into the distance read-out */
	private float nameBoxWidth() {
		float box = PANEL_WIDTH - PADDING * 2.0f;

		if (this.showDistance.get() && !this.shownDistance.isEmpty()) {
			box -= RenderUtils.textWidth(this.shownDistance) + NAME_GAP;
		}

		return Math.max(0.0f, box);
	}

	private void drawPanel(DrawContext context, float x, float y) {
		float alpha = Animations.clamp01(this.visibility);
		float radius = cornerRadius();
		float right = x + PANEL_WIDTH - PADDING;
		float textY = y + TEXT_Y + (TEXT_BOX - RenderUtils.fontHeight()) / 2.0f + 1.0f;
		float fill = Animations.clamp01(this.shownHealth);
		float trail = Animations.clamp01(Math.max(this.trailHealth, fill));
		int barColor = RenderUtils.mix(healthColor(this.shownHealth), FLASH_COLOR, this.flash * 0.75f);

		RenderUtils.drawGlassPanel(context, x, y, PANEL_WIDTH, PANEL_HEIGHT, radius,
				RenderUtils.multiplyAlpha(Theme.glassTop(), alpha), RenderUtils.multiplyAlpha(Theme.glassBottom(), alpha),
				RenderUtils.multiplyAlpha(RenderUtils.mix(Theme.glassBorder(), FLASH_BORDER, this.flash), alpha),
				RenderUtils.multiplyAlpha(Theme.glassShadow(), alpha), GLASS_SHADOW_SPREAD);
		RenderUtils.drawAccentWash(context, x, y, PANEL_WIDTH, PANEL_HEIGHT, radius,
				ConfigManager.get().accentColor,
				alpha * (0.85f + Animations.breathe(ACCENT_PULSE_MILLIS, 0.0f) * 0.30f));

		if (this.flash > 0.01f) {
			// A hit washes the panel red for a moment, which registers without having to read the bar.
			RenderUtils.drawAccentWash(context, x, y, PANEL_WIDTH, PANEL_HEIGHT, radius, FLASH_COLOR,
					this.flash * 0.55f * alpha);
		}

		if (this.showDistance.get()) {
			RenderUtils.drawTextVCentered(context, this.shownDistance, right, y + TEXT_Y, TEXT_BOX,
					RenderUtils.multiplyAlpha(themed(Theme.textDim()), alpha), true, Align.RIGHT);
		}

		RenderUtils.drawMarqueeText(context, this.shownName, x + PADDING, textY, nameBoxWidth(),
				this.marqueeOffset, RenderUtils.multiplyAlpha(themed(Theme.text()), alpha), true);
		RenderUtils.drawTextVCentered(context,
				String.format(Locale.ROOT, "%.1f", this.shownHealth * this.shownMaxHealth), right,
				y + BAR_TOP - (TEXT_BOX - BAR_HEIGHT) * 0.5f, TEXT_BOX,
				RenderUtils.multiplyAlpha(barColor, alpha), true, Align.RIGHT);

		drawHealthBar(context, x, y, alpha, fill, trail, barColor);
	}

	private void drawHealthBar(DrawContext context, float x, float y, float alpha, float fill, float trail,
							   int barColor) {
		float barX = x + PADDING;
		float barY = y + BAR_TOP;
		float barWidth = PANEL_WIDTH - PADDING * 2.0f - HEALTH_COLUMN;

		RenderUtils.drawProgressBar(context, barX, barY, barWidth, BAR_HEIGHT, fill,
				RenderUtils.multiplyAlpha(BAR_BG, alpha), RenderUtils.multiplyAlpha(barColor, alpha));

		if (trail > fill + 0.005f) {
			RenderUtils.drawRect(context, barX + barWidth * fill, barY + 1.0f,
					barWidth * (trail - fill), BAR_HEIGHT - 2.0f,
					RenderUtils.multiplyAlpha(BAR_TRAIL, alpha));
		}

		RenderUtils.drawRoundedRect(context, barX + 1.0f, barY + 1.0f, barWidth - 2.0f, 1.0f, 0.5f,
				RenderUtils.multiplyAlpha(BAR_HIGHLIGHT, alpha));
	}

	private static double distanceTo(MinecraftClient client, LivingEntity entity) {
		Box box = entity.getBoundingBox();

		return Math.sqrt(client.player.squaredDistanceTo(
				(box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5));
	}
}
