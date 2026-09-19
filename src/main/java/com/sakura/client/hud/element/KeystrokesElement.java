package com.sakura.client.hud.element;

import com.sakura.client.hud.HudAnchor;
import com.sakura.client.hud.HudModule;
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
 */
public class KeystrokesElement extends HudModule {

	private static final float KEY_SIZE = 24.0f;
	private static final float MOUSE_KEY_HEIGHT = 28.0f;
	private static final float GAP = 4.0f;
	private static final float MARGIN = 4.0f;
	private static final float KEY_RADIUS = 4.0f;
	private static final long CPS_WINDOW_MILLIS = 1000L;

	private static final int KEY_BG = 0x99000000;
	private static final int KEY_BG_PRESSED = 0xE6FFFFFF;
	private static final int KEY_BORDER = 0x1AFFFFFF;
	private static final int KEY_TEXT = 0xFFFFFFFF;
	private static final int KEY_TEXT_PRESSED = 0xFF101010;

	private final Deque<Long> attackClicks = new ArrayDeque<>();
	private final Deque<Long> useClicks = new ArrayDeque<>();
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

		float gridWidth = getWidth();
		float col0 = x;
		float col1 = x + KEY_SIZE + GAP;
		float col2 = x + (KEY_SIZE + GAP) * 2.0f;
		float row0 = y;
		float row1 = row0 + KEY_SIZE + GAP;
		float row2 = row1 + KEY_SIZE + GAP;
		float row3 = row2 + KEY_SIZE + GAP;

		drawKey(context, col1, row0, KEY_SIZE, KEY_SIZE, "W", null, client.options.forwardKey.isPressed());
		drawKey(context, col0, row1, KEY_SIZE, KEY_SIZE, "A", null, client.options.leftKey.isPressed());
		drawKey(context, col1, row1, KEY_SIZE, KEY_SIZE, "S", null, client.options.backKey.isPressed());
		drawKey(context, col2, row1, KEY_SIZE, KEY_SIZE, "D", null, client.options.rightKey.isPressed());
		drawKey(context, col0, row2, gridWidth, KEY_SIZE, "Space", null, client.options.jumpKey.isPressed());

		float mouseKeyWidth = (gridWidth - GAP) / 2.0f;
		drawKey(context, col0, row3, mouseKeyWidth, MOUSE_KEY_HEIGHT, "LMB",
				String.valueOf(this.attackClicks.size()), client.options.attackKey.isPressed());
		drawKey(context, col0 + mouseKeyWidth + GAP, row3, mouseKeyWidth, MOUSE_KEY_HEIGHT, "RMB",
				String.valueOf(this.useClicks.size()), client.options.useKey.isPressed());
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

	private static void drawKey(DrawContext context, float x, float y, float width, float height, String label,
								String subLabel, boolean pressed) {
		int background = pressed ? KEY_BG_PRESSED : KEY_BG;
		int textColor = pressed ? KEY_TEXT_PRESSED : KEY_TEXT;

		RenderUtils.drawRoundedRect(context, x, y, width, height, KEY_RADIUS, background);
		RenderUtils.drawBorder(context, x, y, width, height, KEY_RADIUS, 1.0f, KEY_BORDER);

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
