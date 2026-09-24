package com.sakura.client.gui.widget;


import com.sakura.client.render.Theme;
import com.sakura.client.module.Module;
import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.RenderUtils.Align;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Click-to-bind key button for a module.
 *
 * <p>Clicking starts listening; the next key press becomes the binding. {@code Escape} cancels the
 * capture and keeps the previous binding, while {@code Delete}/{@code Backspace} clears it. The key
 * name itself comes from vanilla's own {@link InputUtil}, so it is localised.</p>
 */
public class KeybindWidget implements GuiWidget {

	private static final float WIDTH = 40.0f;
	private static final float HEIGHT = 12.0f;
	private int keyCode;
	private boolean listening;

	private float x;
	private float y;
	private Runnable onChanged;

	public KeybindWidget(int keyCode) {
		this.keyCode = keyCode;
	}

	public int getKeyCode() {
		return this.keyCode;
	}

	public boolean isListening() {
		return this.listening;
	}

	/** Invoked after a successful capture so the caller can persist the new binding. */
	public void setOnChanged(Runnable onChanged) {
		this.onChanged = onChanged;
	}

	public static float widgetWidth() {
		return WIDTH;
	}

	public static float widgetHeight() {
		return HEIGHT;
	}

	/** Draws the button with its top-left corner at ({@code x}, {@code y}). */
	public void draw(DrawContext context, float x, float y, double mouseX, double mouseY) {
		this.x = x;
		this.y = y;

		boolean hovered = contains(mouseX, mouseY);
		int background = this.listening ? Theme.accentAlpha(0x33) : (hovered ? Theme.buttonBgHover() : Theme.buttonBg());

		RenderUtils.drawRoundedRect(context, x, y, WIDTH, HEIGHT, 3.0f, background);
		RenderUtils.drawBorder(context, x, y, WIDTH, HEIGHT, 3.0f, 1.0f, Theme.sidebarDivider());
		RenderUtils.drawTextVCentered(context, getLabel(), x + WIDTH / 2.0f, y, HEIGHT,
				this.listening ? Theme.text() : Theme.textMuted(), false, Align.CENTER);
	}

	public String getLabel() {
		if (this.listening) {
			return "...";
		}

		return this.keyCode == Module.UNBOUND ? "None" : keyName(this.keyCode);
	}

	/** Localised key name, falling back to the raw code if vanilla has no mapping. */
	public static String keyName(int keyCode) {
		try {
			return InputUtil.fromKeyCode(new KeyInput(keyCode, 0, 0)).getLocalizedText().getString();
		} catch (RuntimeException exception) {
			return "Key " + keyCode;
		}
	}

	// -------------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(Click click) {
		boolean hit = contains(click.x(), click.y());

		if (this.listening) {
			this.listening = false;
		}

		if (hit) {
			this.listening = true;
			return true;
		}

		return false;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (!this.listening) {
			return false;
		}

		int code = input.key();
		this.listening = false;

		if (code == GLFW.GLFW_KEY_ESCAPE) {
			// Cancel the capture, keep whatever was bound before.
			return true;
		}

		if (code == GLFW.GLFW_KEY_DELETE || code == GLFW.GLFW_KEY_BACKSPACE) {
			this.keyCode = Module.UNBOUND;
		} else {
			this.keyCode = code;
		}

		if (this.onChanged != null) {
			this.onChanged.run();
		}

		return true;
	}

	private boolean contains(double mouseX, double mouseY) {
		return mouseX >= this.x && mouseX < this.x + WIDTH && mouseY >= this.y && mouseY < this.y + HEIGHT;
	}
}
