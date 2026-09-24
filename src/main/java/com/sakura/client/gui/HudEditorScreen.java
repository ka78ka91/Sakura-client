package com.sakura.client.gui;


import com.sakura.client.render.Theme;
import com.sakura.client.hud.HudManager;
import com.sakura.client.hud.HudModule;
import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.RenderUtils.Align;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Drag-and-drop layout screen for the HUD.
 *
 * <p>Every element is drawn in place over a subtle grid so the layout can be judged against the real
 * game view. Left-drag moves an element, right-click sends it back to its default anchor, and closing
 * the screen writes the layout to the config.</p>
 */
public class HudEditorScreen extends Screen {

	private static final float HINT_HEIGHT = 24.0f;
	private static final float GRID_SPACING = 20.0f;
	private HudModule dragging;
	private float grabOffsetX;
	private float grabOffsetY;
	private boolean moved;

	public HudEditorScreen() {
		super(Text.literal("HUD Editor"));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// Vanilla's Screen.renderWithTooltip already painted the blurred, darkened background.
		super.render(context, mouseX, mouseY, delta);

		int screenWidth = context.getScaledWindowWidth();
		int screenHeight = context.getScaledWindowHeight();

		drawGrid(context, screenWidth, screenHeight);
		HudManager.renderEditorPreview(context);

		HudModule highlighted = this.dragging != null
				? this.dragging
				: HudManager.getAt(mouseX, mouseY, screenWidth, screenHeight);

		if (highlighted != null) {
			float x = highlighted.resolveX(screenWidth);
			float y = highlighted.resolveY(screenHeight);

			RenderUtils.drawBorder(context, x, y, highlighted.getWidth(), highlighted.getHeight(), 3.0f, 1.0f,
					highlighted.isEnabled() ? Theme.accentAlpha(0xCC) : Theme.textFaint());
			RenderUtils.drawText(context, highlighted.getName(), x, y - 11.0f, Theme.text(), true);
		}

		drawHint(context, screenWidth, screenHeight);
	}

	private static void drawGrid(DrawContext context, int screenWidth, int screenHeight) {
		for (float x = 0.0f; x < screenWidth; x += GRID_SPACING) {
			RenderUtils.drawRect(context, x, 0.0f, 1.0f, screenHeight, Theme.sidebarDivider());
		}

		for (float y = 0.0f; y < screenHeight; y += GRID_SPACING) {
			RenderUtils.drawRect(context, 0.0f, y, screenWidth, 1.0f, Theme.sidebarDivider());
		}
	}

	private static void drawHint(DrawContext context, int screenWidth, int screenHeight) {
		String line = "Drag an element to move it  \u00B7  Right-click to reset  \u00B7  Esc to go back";
		float width = RenderUtils.textWidth(line) + 26.0f;
		float x = (screenWidth - width) / 2.0f;
		float y = screenHeight - HINT_HEIGHT - 8.0f;

		RenderUtils.drawRoundedRect(context, x, y, width, HINT_HEIGHT, 6.0f, Theme.sectionBg());
		RenderUtils.drawTextVCentered(context, line, x + width / 2.0f, y, HINT_HEIGHT, Theme.textDim(), false, Align.CENTER);
	}

	// -------------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		HudModule target = HudManager.getAt(click.x(), click.y(), this.width, this.height);

		if (target == null) {
			return super.mouseClicked(click, doubled);
		}

		if (click.button() == 1) {
			target.clearPosition();
			HudManager.savePositions();
			return true;
		}

		if (click.button() == 0) {
			this.dragging = target;
			this.grabOffsetX = (float) click.x() - target.resolveX(this.width);
			this.grabOffsetY = (float) click.y() - target.resolveY(this.height);
			return true;
		}

		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (this.dragging == null) {
			return super.mouseDragged(click, offsetX, offsetY);
		}

		this.dragging.setPosition((float) click.x() - this.grabOffsetX, (float) click.y() - this.grabOffsetY);
		this.moved = true;
		return true;
	}

	@Override
	public boolean mouseReleased(Click click) {
		if (this.dragging == null) {
			return super.mouseReleased(click);
		}

		this.dragging = null;

		if (this.moved) {
			HudManager.savePositions();
			this.moved = false;
		}

		return true;
	}

	/** Always returns to the main menu, saving the layout on the way out. */
	@Override
	public void close() {
		HudManager.savePositions();
		MinecraftClient.getInstance().setScreen(new ClickGuiScreen());
	}
}
