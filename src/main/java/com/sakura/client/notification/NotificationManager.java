package com.sakura.client.notification;

import com.sakura.client.module.Module;
import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.RenderUtils.Align;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Short-lived on-screen notices, mainly the module toggle confirmation.
 *
 * <p>Toggling a module with a key bind gives no feedback otherwise, and a module that silently fails to do
 * anything is indistinguishable from a broken one. Notices are drawn both by the HUD (key bind toggles) and by
 * {@code ClickGuiScreen} (toggles made in the menu, which the HUD does not render over).</p>
 */
public final class NotificationManager {

	private static final List<Notice> NOTICES = new ArrayList<>();

	private static final int LIFETIME_TICKS = 45;
	private static final int FADE_TICKS = 10;
	private static final int MAX_NOTICES = 5;

	private static final float WIDTH = 132.0f;
	private static final float HEIGHT = 26.0f;
	private static final float GAP = 4.0f;

	private static final int BG = 0xE6141414;
	private static final int BORDER = 0x1AFFFFFF;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFAAAAAA;
	private static final int ENABLED = 0xFFA06EFF;
	private static final int DISABLED = 0xFF808080;

	private NotificationManager() {
	}

	private record Notice(String title, String subtitle, boolean positive, int age) {

		Notice aged() {
			return new Notice(this.title, this.subtitle, this.positive, this.age + 1);
		}
	}

	/** Announces a module being switched on or off. */
	public static void showModuleToggle(Module module) {
		String subtitle = module.isEnabled() ? module.getCategory().getDisplayName() : "disabled";
		show(module.getName(), subtitle, module.isEnabled());
	}

	public static void show(String title, String subtitle, boolean positive) {
		NOTICES.add(0, new Notice(title, subtitle, positive, 0));

		while (NOTICES.size() > MAX_NOTICES) {
			NOTICES.remove(NOTICES.size() - 1);
		}
	}

	/** Ages every notice; called once per client tick. */
	public static void tick() {
		if (NOTICES.isEmpty()) {
			return;
		}

		NOTICES.replaceAll(Notice::aged);
		NOTICES.removeIf(notice -> notice.age() > LIFETIME_TICKS);
	}

	public static void clear() {
		NOTICES.clear();
	}

	public static boolean isEmpty() {
		return NOTICES.isEmpty();
	}

	/**
	 * Draws the notice stack with its top-right corner at ({@code rightX}, {@code topY}).
	 *
	 * @param uiScale multiplier applied to every dimension, so notices keep the same apparent size whether
	 *                they are drawn in the HUD's coordinate space or inside the scaled menu window
	 */
	public static void render(DrawContext context, float rightX, float topY, float uiScale) {
		if (NOTICES.isEmpty() || uiScale <= 0.0f) {
			return;
		}

		context.getMatrices().pushMatrix();
		context.getMatrices().translate(rightX, topY);
		context.getMatrices().scale(uiScale, uiScale);

		for (int index = 0; index < NOTICES.size(); index++) {
			Notice notice = NOTICES.get(index);

			if (notice.age() > LIFETIME_TICKS) {
				continue;
			}

			float alpha = alphaOf(notice.age());

			if (alpha <= 0.01f) {
				continue;
			}

			float x = -WIDTH;
			float y = index * (HEIGHT + GAP);

			RenderUtils.drawRoundedRect(context, x, y, WIDTH, HEIGHT, 5.0f, applyAlpha(BG, alpha));
			RenderUtils.drawBorder(context, x, y, WIDTH, HEIGHT, 5.0f, 1.0f, applyAlpha(BORDER, alpha));
			RenderUtils.drawTextVCentered(context, notice.title(), x + 9.0f, y + 2.0f, 13.0f,
					applyAlpha(TEXT, alpha), false, Align.LEFT);
			RenderUtils.drawTextVCentered(context, notice.subtitle(), x + 9.0f, y + 13.0f, 11.0f,
					applyAlpha(notice.positive() ? ENABLED : TEXT_DIM, alpha), false, Align.LEFT);
			RenderUtils.drawRoundedRect(context, x + 3.0f, y + 5.0f, 2.0f, HEIGHT - 10.0f, 1.0f,
					applyAlpha(notice.positive() ? ENABLED : DISABLED, alpha));
		}

		context.getMatrices().popMatrix();
	}

	/** Fades in over the first ticks and out over the last ones. */
	private static float alphaOf(int age) {
		int remaining = LIFETIME_TICKS - age;

		if (remaining <= 0) {
			return 0.0f;
		}

		if (age < FADE_TICKS) {
			return age / (float) FADE_TICKS;
		}

		if (remaining < FADE_TICKS) {
			return remaining / (float) FADE_TICKS;
		}

		return 1.0f;
	}

	private static int applyAlpha(int argb, float alpha) {
		int base = (argb >>> 24) & 0xFF;
		int scaled = Math.round(base * Math.clamp(alpha, 0.0f, 1.0f));
		return (scaled << 24) | (argb & 0xFFFFFF);
	}
}
