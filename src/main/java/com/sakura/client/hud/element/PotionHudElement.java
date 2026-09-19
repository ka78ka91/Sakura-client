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
 */
public final class PotionHudElement extends HudModule {

	private static final float ROW_HEIGHT = 11.0f;
	private static final float PADDING = 5.0f;
	private static final float MIN_WIDTH = 96.0f;
	private static final int PANEL_BG = 0x66000000;
	private static final int NAME_COLOR = 0xFFFFFFFF;
	private static final int DURATION_COLOR = 0xFFB0B0B0;
	private static final String[] ROMAN = {"", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

	private final BooleanSetting showDuration = setting(new BooleanSetting("Duration",
			"Show how long each effect still lasts.", true));
	private final NumberSetting maxEffects = setting(new NumberSetting("Max Effects",
			"Maximum number of effects listed; the ones about to expire come first.", 8.0, 1.0, 16.0, 1.0, ""));

	private final List<StatusEffectInstance> shown = new ArrayList<>();

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
			float row = RenderUtils.textWidth(label(effect)) + 8.0f
					+ RenderUtils.textWidth(duration(effect)) + PADDING * 2.0f;
			width = Math.max(width, row);
		}

		return width;
	}

	@Override
	public float getHeight() {
		refresh();

		if (this.shown.isEmpty()) {
			return ROW_HEIGHT + PADDING * 2.0f;
		}

		return this.shown.size() * ROW_HEIGHT + PADDING * 2.0f;
	}

	@Override
	public void render(DrawContext context, float x, float y) {
		refresh();

		if (this.shown.isEmpty()) {
			return;
		}

		float width = getWidth();
		float height = getHeight();

		RenderUtils.drawRoundedRect(context, x, y, width, height, cornerRadius(), PANEL_BG);

		for (int index = 0; index < this.shown.size(); index++) {
			StatusEffectInstance effect = this.shown.get(index);
			float rowY = y + PADDING + index * ROW_HEIGHT;

			RenderUtils.drawTextVCentered(context, label(effect), x + PADDING, rowY, ROW_HEIGHT,
					NAME_COLOR, true, Align.LEFT);

			if (this.showDuration.get()) {
				RenderUtils.drawTextVCentered(context, duration(effect), x + width - PADDING, rowY, ROW_HEIGHT,
						DURATION_COLOR, true, Align.RIGHT);
			}
		}
	}

	private String label(StatusEffectInstance effect) {
		return effect.getEffectType().value().getName().getString() + amplifierSuffix(effect.getAmplifier());
	}

	private String duration(StatusEffectInstance effect) {
		return effect.isInfinite() ? "∞" : formatDuration(effect.getDuration());
	}
}
