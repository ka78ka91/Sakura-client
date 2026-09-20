package com.sakura.client.hud.element;

import com.sakura.client.config.ConfigManager;
import com.sakura.client.hud.HudAnchor;
import com.sakura.client.hud.HudModule;
import com.sakura.client.render.Animations;
import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.RenderUtils.Align;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * WASD / space / mouse keystroke overlay with live clicks-per-second counters.
 *
 * <p>A press edge appends a timestamp to a deque and samples older than one second are dropped, so the
 * deque size is the current CPS. Edges are observed from the render loop, which runs more often than
 * the tick loop.</p>
 *
 * <p>Every key is a pane of frosted glass that lights up as it is held. The highlight is a float per key
 * eased toward "pressed" rather than a boolean, so a key ramps up when it goes down and eases back when it is
 * released — key presses are far shorter than a frame at a low frame rate, and a hard switch would make them
 * blink rather than flash.</p>
 */
public class KeystrokesElement extends HudModule {

	private static final float KEY_SIZE = 24.0f;
	private static final float MOUSE_KEY_HEIGHT = 28.0f;
	private static final float GAP = 4.0f;
	private static final float MARGIN = 4.0f;
	private static final float KEY_RADIUS = 4.0f;
	private static final long CPS_WINDOW_MILLIS = 1000L;
	/** Press lights a key up quickly, release lets it fall back more gently. */
	private static final float PRESS_IN_SPEED = 22.0f;
	private static final float PRESS_OUT_SPEED = 11.0f;

	private static final int KEY_BORDER = 0x1AFFFFFF;
	private static final int KEY_BORDER_PRESSED = 0x66FFFFFF;
	private static final int KEY_TEXT = 0xFFFFFFFF;
	private static final int KEY_TEXT_PRESSED = 0xFF121216;
	/** A held key turns into white frosted glass instead of simply changing colour. */
	private static final int KEY_GLASS_PRESSED_TOP = 0xE6FFFFFF;
	private static final int KEY_GLASS_PRESSED_BOTTOM = 0xD9EEEFF6;

	/** Glass body: a dark, slightly cool gradient drawn over the blurred world. */
	private static final int GLASS_TOP = 0xB414141A;
	private static final int GLASS_BOTTOM = 0x8C0A0A0F;
	private static final int GLASS_SHADOW = 0x66000000;
	private static final float GLASS_SHADOW_SPREAD = 2.5f;

	private static final int KEY_FORWARD = 0;
	private static final int KEY_LEFT = 1;
	private static final int KEY_BACK = 2;
	private static final int KEY_RIGHT = 3;
	private static final int KEY_JUMP = 4;
	private static final int KEY_ATTACK = 5;
	private static final int KEY_USE = 6;
	private static final int KEY_COUNT = 7;

	private final Deque<Long> attackClicks = new ArrayDeque<>();
	private final Deque<Long> useClicks = new ArrayDeque<>();
	private final Animations.Clock clock = new Animations.Clock();
	private final float[] press = new float[KEY_COUNT];
	private boolean attackWasPressed;
	private boolean useWasPressed;

	public KeystrokesElement() {
		super("Keystrokes", "WASD, space and mouse buttons with live CPS",
				HudAnchor.BOTTOM_RIGHT, MARGIN, MARGIN, true);
	}

	@Override
	public float getWidth() {
		return KEY_SIZE * 3.0f + GAP * 2.0f;
	}

	@Override
	public float getHeight() {
		return KEY_SIZE * 3.0f + MOUSE_KEY_HEIGHT + GAP * 3.0f;
	}

	@Override
	public void render(DrawContext context, float x, float y) {
		MinecraftClient client = MinecraftClient.getInstance();

		if (client.options == null) {
			return;
		}

		trackClicks(client);

		float delta = this.clock.tick();

		updatePress(KEY_FORWARD, client.options.forwardKey, delta);
		updatePress(KEY_LEFT, client.options.leftKey, delta);
		updatePress(KEY_BACK, client.options.backKey, delta);
		updatePress(KEY_RIGHT, client.options.rightKey, delta);
		updatePress(KEY_JUMP, client.options.jumpKey, delta);
		updatePress(KEY_ATTACK, client.options.attackKey, delta);
		updatePress(KEY_USE, client.options.useKey, delta);

		float gridWidth = getWidth();
		float col0 = x;
		float col1 = x + KEY_SIZE + GAP;
		float col2 = x + (KEY_SIZE + GAP) * 2.0f;
		float row0 = y;
		float row1 = row0 + KEY_SIZE + GAP;
		float row2 = row1 + KEY_SIZE + GAP;
		float row3 = row2 + KEY_SIZE + GAP;

		drawKey(context, col1, row0, KEY_SIZE, KEY_SIZE, "W", null, this.press[KEY_FORWARD]);
		drawKey(context, col0, row1, KEY_SIZE, KEY_SIZE, "A", null, this.press[KEY_LEFT]);
		drawKey(context, col1, row1, KEY_SIZE, KEY_SIZE, "S", null, this.press[KEY_BACK]);
		drawKey(context, col2, row1, KEY_SIZE, KEY_SIZE, "D", null, this.press[KEY_RIGHT]);
		drawKey(context, col0, row2, gridWidth, KEY_SIZE, "Space", null, this.press[KEY_JUMP]);

		float mouseKeyWidth = (gridWidth - GAP) / 2.0f;
		drawKey(context, col0, row3, mouseKeyWidth, MOUSE_KEY_HEIGHT, "LMB",
				String.valueOf(this.attackClicks.size()), this.press[KEY_ATTACK]);
		drawKey(context, col0 + mouseKeyWidth + GAP, row3, mouseKeyWidth, MOUSE_KEY_HEIGHT, "RMB",
				String.valueOf(this.useClicks.size()), this.press[KEY_USE]);
	}

	private void trackClicks(MinecraftClient client) {
		long now = System.currentTimeMillis();

		this.attackWasPressed = sample(client.options.attackKey, this.attackWasPressed, this.attackClicks, now);
		this.useWasPressed = sample(client.options.useKey, this.useWasPressed, this.useClicks, now);
	}

	private static boolean sample(KeyBinding key, boolean wasPressed, Deque<Long> clicks, long now) {
		boolean pressed = key.isPressed();

		if (pressed && !wasPressed) {
			clicks.addLast(now);
		}

		while (!clicks.isEmpty() && now - clicks.peekFirst() > CPS_WINDOW_MILLIS) {
			clicks.removeFirst();
		}

		return pressed;
	}

	/** Eases one key's highlight toward 1 while it is held and back toward 0 once it is released. */
	private void updatePress(int index, KeyBinding key, float delta) {
		float target = key.isPressed() ? 1.0f : 0.0f;
		float speed = target > this.press[index] ? PRESS_IN_SPEED : PRESS_OUT_SPEED;

		this.press[index] = Animations.approach(this.press[index], target, speed, delta);
	}

	/**
	 * Draws one glass key.
	 *
	 * @param pressAmount 0 while released, 1 while held; everything that reacts to the key reads this
	 */
	private static void drawKey(DrawContext context, float x, float y, float width, float height, String label,
								String subLabel, float pressAmount) {
		float eased = Animations.easeOutCubic(pressAmount);
		int textColor = RenderUtils.mix(KEY_TEXT, KEY_TEXT_PRESSED, eased);

		RenderUtils.drawGlassPanel(context, x, y, width, height, KEY_RADIUS,
				RenderUtils.mix(GLASS_TOP, KEY_GLASS_PRESSED_TOP, eased),
				RenderUtils.mix(GLASS_BOTTOM, KEY_GLASS_PRESSED_BOTTOM, eased),
				RenderUtils.mix(KEY_BORDER, KEY_BORDER_PRESSED, eased),
				RenderUtils.multiplyAlpha(GLASS_SHADOW, 1.0f - eased * 0.55f), GLASS_SHADOW_SPREAD);
		RenderUtils.drawAccentWash(context, x, y, width, height, KEY_RADIUS,
				ConfigManager.get().accentColor, 0.30f + eased * 0.70f);

		float centerX = x + width / 2.0f;

		if (subLabel == null) {
			RenderUtils.drawTextVCentered(context, label, centerX, y, height, textColor, false, Align.CENTER);
			return;
		}

		RenderUtils.drawTextVCentered(context, label, centerX, y, height / 2.0f, textColor, false, Align.CENTER);
		RenderUtils.drawTextVCentered(context, subLabel, centerX, y + height / 2.0f, height / 2.0f,
				textColor, false, Align.CENTER);
	}
}
