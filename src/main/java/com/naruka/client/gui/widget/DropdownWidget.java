package com.naruka.client.gui.widget;

import com.naruka.client.render.RenderUtils;
import com.naruka.client.render.RenderUtils.Align;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;

/**
 * Single-line dropdown: {@code label ... current value ›}.
 *
 * <p>Clicking the row expands an option list directly underneath it. The list grows with a short
 * animation and is clipped with {@link DrawContext#enableScissor(int, int, int, int)} so the
 * opening does not spill over neighbouring rows.</p>
 */
public class DropdownWidget implements GuiWidget {

	private static final float ROW_HEIGHT = 20.0f;
	private static final float OPTION_HEIGHT = 18.0f;
	private static final float LIST_PADDING = 4.0f;
	private static final float LIST_RADIUS = 6.0f;
	private static final float ARROW_WIDTH = 16.0f;
	private static final float VALUE_GAP = 8.0f;
	private static final float ANIMATION_FACTOR = 0.25f;
	private static final float ANIMATION_EPSILON = 0.01f;

	private static final int LABEL_COLOR = 0xFFFFFFFF;
	private static final int VALUE_COLOR = 0xFFAAAAAA;
	private static final int ARROW_COLOR = 0xFFAAAAAA;
	private static final int LIST_BG = 0xE0141414;
	private static final int LIST_BORDER = 0x1AFFFFFF;
	private static final int OPTION_TEXT = 0xFFCCCCCC;
	private static final int OPTION_TEXT_HOVER = 0xFFFFFFFF;
	private static final int OPTION_TEXT_SELECTED = 0xFFFFFFFF;
	private static final int OPTION_HOVER_BG = 0x20FFFFFF;
	private static final int OPTION_SELECTED_BG = 0x33A06EFF;

	private final String label;
	private final String[] options;

	private int selected;
	private boolean expanded;
	private float animation;

	private float x;
	private float y;
	private float width;

	public DropdownWidget(String label, String... options) {
		if (options == null || options.length == 0) {
			throw new IllegalArgumentException("DropdownWidget requires at least one option");
		}

		this.label = label;
		this.options = options.clone();
	}

	public String getLabel() {
		return label;
	}

	public String getValue() {
		return this.options[this.selected];
	}

	public int getSelectedIndex() {
		return this.selected;
	}

	public void setSelectedIndex(int index) {
		this.selected = Math.max(0, Math.min(this.options.length - 1, index));
	}

	public boolean isExpanded() {
		return expanded;
	}

	public void setExpanded(boolean expanded) {
		this.expanded = expanded;
	}

	/** Row height while collapsed, or row plus option list while expanded. */
	public float getHeight() {
		return this.expanded ? ROW_HEIGHT + listHeight() : ROW_HEIGHT;
	}

	private float listHeight() {
		return this.options.length * OPTION_HEIGHT + LIST_PADDING * 2.0f;
	}

	/** Draws the row with its top-left corner at ({@code x}, {@code y}). */
	public void draw(DrawContext context, float x, float y, float width, double mouseX, double mouseY) {
		this.x = x;
		this.y = y;
		this.width = width;

		float target = this.expanded ? 1.0f : 0.0f;
		this.animation += (target - this.animation) * ANIMATION_FACTOR;

		if (Math.abs(target - this.animation) < ANIMATION_EPSILON) {
			this.animation = target;
		}

		RenderUtils.drawTextVCentered(context, this.label, x, y, ROW_HEIGHT, LABEL_COLOR, false, Align.LEFT);
		RenderUtils.drawTextVCentered(context, getValue(), x + width - ARROW_WIDTH - VALUE_GAP, y, ROW_HEIGHT,
				VALUE_COLOR, false, Align.RIGHT);
		RenderUtils.drawTextVCentered(context, "\u203A", x + width, y, ROW_HEIGHT, ARROW_COLOR, false, Align.RIGHT);

		if (this.animation > ANIMATION_EPSILON) {
			drawOptions(context, mouseX, mouseY);
		}
	}

	private void drawOptions(DrawContext context, double mouseX, double mouseY) {
		float listTop = this.y + ROW_HEIGHT;
		float fullHeight = listHeight();
		float visibleHeight = fullHeight * this.animation;

		// Clipped through LocalTransform: the option list must be cut off in screen space, and this widget only
		// knows its local bounds.
		com.naruka.client.render.LocalTransform.enableScissor(context, this.x, listTop, this.width, visibleHeight);

		RenderUtils.drawRoundedRect(context, this.x, listTop, this.width, fullHeight, LIST_RADIUS, LIST_BG);
		RenderUtils.drawBorder(context, this.x, listTop, this.width, fullHeight, LIST_RADIUS, 1.0f, LIST_BORDER);

		for (int i = 0; i < this.options.length; i++) {
			float optionY = listTop + LIST_PADDING + i * OPTION_HEIGHT;
			boolean hovered = isOverOption(mouseX, mouseY, i);

			if (i == this.selected) {
				RenderUtils.drawRoundedRect(context, this.x + 2.0f, optionY, this.width - 4.0f, OPTION_HEIGHT,
						4.0f, OPTION_SELECTED_BG);
			} else if (hovered) {
				RenderUtils.drawRoundedRect(context, this.x + 2.0f, optionY, this.width - 4.0f, OPTION_HEIGHT,
						4.0f, OPTION_HOVER_BG);
			}

			int color = (i == this.selected) ? OPTION_TEXT_SELECTED : (hovered ? OPTION_TEXT_HOVER : OPTION_TEXT);
			RenderUtils.drawTextVCentered(context, this.options[i], this.x + 12.0f, optionY, OPTION_HEIGHT,
					color, false, Align.LEFT);
		}

		context.disableScissor();
	}

	// -------------------------------------------------------------------- input

	public boolean mouseClicked(Click click) {
		double mouseX = click.x();
		double mouseY = click.y();

		if (mouseX >= this.x && mouseX < this.x + this.width
				&& mouseY >= this.y && mouseY < this.y + ROW_HEIGHT) {
			this.expanded = !this.expanded;
			return true;
		}

		if (!this.expanded) {
			return false;
		}

		for (int i = 0; i < this.options.length; i++) {
			if (isOverOption(mouseX, mouseY, i)) {
				this.selected = i;
				this.expanded = false;
				return true;
			}
		}

		// A click anywhere below the row dismisses the popup.
		if (mouseY > this.y + ROW_HEIGHT) {
			this.expanded = false;
			return true;
		}

		return false;
	}

	private boolean isOverOption(double mouseX, double mouseY, int index) {
		float optionY = this.y + ROW_HEIGHT + LIST_PADDING + index * OPTION_HEIGHT;

		return mouseX >= this.x && mouseX < this.x + this.width
				&& mouseY >= optionY && mouseY < optionY + OPTION_HEIGHT;
	}
}
