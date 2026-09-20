package com.sakura.client.hud.element;

import com.sakura.client.config.ConfigManager;
import com.sakura.client.hud.HudAnchor;
import com.sakura.client.hud.HudModule;
import com.sakura.client.render.Animations;
import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.RenderUtils.Align;
import com.sakura.client.setting.BooleanSetting;
import com.sakura.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Lists the player's active status effects with their amplifier and remaining time.
 *
 * <p>Ported from LiquidBounce's potion HUD (GPL-3.0). The list is sorted by remaining time so the effect
 * about to run out is on top, which is the one a player needs to watch during a fight, and it is capped so a
 * pile of effects cannot cover the screen.</p>
 *
 * <p>Each row carries a thin bar showing how much of the effect is left, scaled against the longest duration
 * that effect has had while it was on screen, and a dot in the effect's own colour. Rows fade in when an
 * effect starts and fade out when it ends, and the panel fades with them, so an effect expiring never makes
 * the list pop out of existence.</p>
 */
public final class PotionHudElement extends HudModule {

	private static final float ROW_HEIGHT = 13.0f;
	/** Text sits in the upper part of a row so the remaining-time bar fits under it. */
	private static final float ROW_TEXT_HEIGHT = 10.0f;
	private static final float ROW_BAR_HEIGHT = 1.5f;
	private static final float ROW_DOT_WIDTH = 2.5f;
	private static final float ROW_DOT_HEIGHT = 7.0f;
	private static final float ROW_DOT_GAP = 4.5f;
	/** Gap between the name and the duration, wide enough that the two never touch. */
	private static final float ROW_TEXT_GAP = 8.0f;
	private static final float PADDING = 5.0f;
	private static final float MIN_WIDTH = 96.0f;
	private static final float ROW_IN_SPEED = 9.0f;
	private static final float ROW_OUT_SPEED = 6.0f;
	private static final float ROW_VISIBLE_EPSILON = 0.02f;

	private static final int NAME_COLOR = 0xFFFFFFFF;
	private static final int DURATION_COLOR = 0xFFB0B0B0;
	private static final int ROW_BAR_TRACK = 0x33FFFFFF;
	private static final String[] ROMAN = {"", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

	/** Glass body: a dark, slightly cool gradient drawn over the blurred world. */
	private static final int GLASS_TOP = 0xB414141A;
	private static final int GLASS_BOTTOM = 0x8C0A0A0F;
	private static final int GLASS_BORDER = 0x2EFFFFFF;
	private static final int GLASS_SHADOW = 0x66000000;
	private static final float GLASS_SHADOW_SPREAD = 4.0f;

	private final BooleanSetting showDuration = setting(new BooleanSetting("Duration",
			"Show how long each effect still lasts.", true));
	private final NumberSetting maxEffects = setting(new NumberSetting("Max Effects",
			"Maximum number of effects listed; the ones about to expire come first.", 8.0, 1.0, 16.0, 1.0, ""));

	private final List<StatusEffectInstance> shown = new ArrayList<>();
	/** Fade state per effect, matched by instance so a row keeps its animation while the list reorders. */
	private final List<RowFade> fades = new ArrayList<>();
	private final Animations.Clock clock = new Animations.Clock();

	public PotionHudElement() {
		super("PotionHUD", "Active status effects", HudAnchor.TOP_LEFT, 4.0f, 100.0f, false);
	}

	/**
	 * @return the effects to list, the one closest to expiring first, capped at {@code limit}. A copy, so the
	 * caller can keep it while the entity's effect collection changes.
	 */
	public static List<StatusEffectInstance> effectsOf(LivingEntity entity, int limit) {
		List<StatusEffectInstance> effects = new ArrayList<>(entity.getStatusEffects());

		// Infinite effects report a negative duration; they sort last because they are never the urgent ones.
		effects.sort(Comparator.comparingInt(effect -> effect.isInfinite() ? Integer.MAX_VALUE : effect.getDuration()));

		if (effects.size() > limit) {
			return new ArrayList<>(effects.subList(0, limit));
		}

		return effects;
	}

	/**
	 * Formats a remaining time the way the inventory screen does.
	 *
	 * @param ticks remaining ticks, negative for an infinite effect
	 */
	public static String formatDuration(int ticks) {
		if (ticks < 0) {
			return "∞";
		}

		int totalSeconds = ticks / 20;
		int hours = totalSeconds / 3600;
		int minutes = totalSeconds % 3600 / 60;
		int seconds = totalSeconds % 60;

		if (hours > 0) {
			return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
		}

		return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
	}

	/** @return the amplifier as a roman numeral, empty for level one, e.g. {@code Speed II} */
	public static String amplifierSuffix(int amplifier) {
		if (amplifier <= 0) {
			return "";
		}

		return amplifier < ROMAN.length ? " " + ROMAN[amplifier] : " " + (amplifier + 1);
	}

	private void refresh() {
		this.shown.clear();

		MinecraftClient client = MinecraftClient.getInstance();
		LivingEntity entity = client.player;

		if (entity == null) {
			return;
		}

		this.shown.addAll(effectsOf(entity, this.maxEffects.get().intValue()));
	}

	@Override
	public float getWidth() {
		refresh();
		float width = MIN_WIDTH;

		for (StatusEffectInstance effect : this.shown) {
			float row = PADDING * 2.0f + ROW_DOT_WIDTH + ROW_DOT_GAP + RenderUtils.textWidth(label(effect))
					+ ROW_TEXT_GAP + RenderUtils.textWidth(duration(effect));
			width = Math.max(width, row);
		}

		return width;
	}

	@Override
	public float getHeight() {
		refresh();

		// Rows that are still fading out keep the panel at its size until they are gone.
		int rows = Math.max(this.shown.size(), fadingRows());

		if (rows == 0) {
			return ROW_HEIGHT + PADDING * 2.0f;
		}

		return rows * ROW_HEIGHT + PADDING * 2.0f;
	}

	@Override
	public void render(DrawContext context, float x, float y) {
		refresh();

		float delta = this.clock.tick();

		advance(delta);

		if (this.shown.isEmpty() && fadingRows() == 0) {
			return;
		}

		float width = getWidth();
		float height = getHeight();
		float bottom = y + height;
		float rowY = y + PADDING;

		drawGlass(context, x, y, width, height, cornerRadius(), panelAlpha());

		for (StatusEffectInstance effect : this.shown) {
			if (rowY + ROW_HEIGHT > bottom) {
				break;
			}

			RowFade fade = fadeOf(effect);

			drawRow(context, effect, x, rowY, width, fade == null ? 1.0f : fade.alpha);
			rowY += ROW_HEIGHT;
		}

		for (RowFade fade : this.fades) {
			if (fade.seen || rowY + ROW_HEIGHT > bottom) {
				continue;
			}

			drawRow(context, fade.effect, x, rowY, width, fade.alpha);
			rowY += ROW_HEIGHT;
		}
	}

	/** Eases every row's alpha, creating the state for effects that just started and dropping the finished. */
	private void advance(float delta) {
		for (RowFade fade : this.fades) {
			fade.seen = false;
		}

		for (StatusEffectInstance effect : this.shown) {
			RowFade fade = fadeOf(effect);

			if (fade == null) {
				fade = new RowFade(effect);
				this.fades.add(fade);
			}

			fade.seen = true;
			fade.track(effect);
			fade.alpha = Animations.approach(fade.alpha, 1.0f, ROW_IN_SPEED, delta);
		}

		for (int index = this.fades.size() - 1; index >= 0; index--) {
			RowFade fade = this.fades.get(index);

			if (fade.seen) {
				continue;
			}

			fade.alpha = Animations.approach(fade.alpha, 0.0f, ROW_OUT_SPEED, delta);

			if (fade.alpha <= ROW_VISIBLE_EPSILON) {
				this.fades.remove(index);
			}
		}
	}

	/** @return how many effects are currently fading out */
	private int fadingRows() {
		int count = 0;

		for (RowFade fade : this.fades) {
			if (!fade.seen && fade.alpha > ROW_VISIBLE_EPSILON) {
				count++;
			}
		}

		return count;
	}

	/** @return the alpha of the panel, taken from its most visible row so it fades with them */
	private float panelAlpha() {
		float alpha = 0.0f;

		for (StatusEffectInstance effect : this.shown) {
			RowFade fade = fadeOf(effect);
			alpha = Math.max(alpha, fade == null ? 1.0f : fade.alpha);
		}

		for (RowFade fade : this.fades) {
			if (!fade.seen) {
				alpha = Math.max(alpha, fade.alpha);
			}
		}

		return Animations.clamp01(alpha);
	}

	private RowFade fadeOf(StatusEffectInstance effect) {
		for (RowFade fade : this.fades) {
			if (fade.effect == effect) {
				return fade;
			}
		}

		return null;
	}

	private void drawRow(DrawContext context, StatusEffectInstance effect, float x, float y, float width,
						 float alpha) {
		RowFade fade = fadeOf(effect);
		float rowAlpha = Animations.clamp01(alpha);
		int accent = RenderUtils.withAlpha(0xFF000000 | effect.getEffectType().value().getColor(), 255);
		float nameX = x + PADDING + ROW_DOT_WIDTH + ROW_DOT_GAP;
		float right = x + width - PADDING;

		// The dot carries the effect's own colour, which is what makes the row recognisable at a glance.
		RenderUtils.drawRoundedRect(context, x + PADDING, y + (ROW_TEXT_HEIGHT - ROW_DOT_HEIGHT) * 0.5f,
				ROW_DOT_WIDTH, ROW_DOT_HEIGHT, ROW_DOT_WIDTH * 0.5f,
				RenderUtils.multiplyAlpha(accent, rowAlpha));

		RenderUtils.drawTextVCentered(context, label(effect), nameX, y, ROW_TEXT_HEIGHT,
				RenderUtils.multiplyAlpha(NAME_COLOR, rowAlpha), true, Align.LEFT);

		if (this.showDuration.get()) {
			RenderUtils.drawTextVCentered(context, duration(effect), right, y, ROW_TEXT_HEIGHT,
					RenderUtils.multiplyAlpha(DURATION_COLOR, rowAlpha), true, Align.RIGHT);
		}

		RenderUtils.drawProgressBar(context, x + PADDING, y + ROW_HEIGHT - ROW_BAR_HEIGHT - 1.0f,
				width - PADDING * 2.0f, ROW_BAR_HEIGHT, remainingFraction(effect, fade),
				RenderUtils.multiplyAlpha(ROW_BAR_TRACK, rowAlpha),
				RenderUtils.multiplyAlpha(RenderUtils.brighten(accent, 0.2f), rowAlpha));
	}

	/** @return how much of the effect is left, scaled against the longest duration it has been seen with */
	private static float remainingFraction(StatusEffectInstance effect, RowFade fade) {
		if (effect.isInfinite() || fade == null || fade.peakDuration <= 0) {
			return 1.0f;
		}

		return Animations.clamp01(Math.max(0, effect.getDuration()) / (float) fade.peakDuration);
	}

	private String label(StatusEffectInstance effect) {
		return effect.getEffectType().value().getName().getString() + amplifierSuffix(effect.getAmplifier());
	}

	private String duration(StatusEffectInstance effect) {
		return effect.isInfinite() ? "∞" : formatDuration(effect.getDuration());
	}

	/** The shared Sakura glass material: gradient body, hairline border, drop shadow and an accent wash. */
	private static void drawGlass(DrawContext context, float x, float y, float width, float height,
								  float radius, float alpha) {
		RenderUtils.drawGlassPanel(context, x, y, width, height, radius,
				RenderUtils.multiplyAlpha(GLASS_TOP, alpha), RenderUtils.multiplyAlpha(GLASS_BOTTOM, alpha),
				RenderUtils.multiplyAlpha(GLASS_BORDER, alpha), RenderUtils.multiplyAlpha(GLASS_SHADOW, alpha),
				GLASS_SHADOW_SPREAD);
		RenderUtils.drawAccentWash(context, x, y, width, height, radius,
				ConfigManager.get().accentColor, alpha);
	}

	/** One effect's fade state, kept across frames while the effect is listed and while it fades out. */
	private static final class RowFade {

		private final StatusEffectInstance effect;
		private float alpha;
		/** Longest duration seen for this effect, the reference the remaining-time bar is scaled against. */
		private int peakDuration;
		/** Set every frame the effect is still listed, which is how a row that has ended is recognised. */
		private boolean seen;

		private RowFade(StatusEffectInstance effect) {
			this.effect = effect;
		}

		private void track(StatusEffectInstance effect) {
			int duration = effect.getDuration();

			if (!effect.isInfinite() && duration > this.peakDuration) {
				this.peakDuration = duration;
			}
		}
	}
}
