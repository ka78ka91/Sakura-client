package com.sakura.client.gui;


import com.sakura.client.config.ConfigManager;
import com.sakura.client.render.Theme;
import com.sakura.client.gui.ClickGuiScreen.Rect;
import com.sakura.client.gui.widget.ColorPickerWidget;
import com.sakura.client.gui.widget.DropdownWidget;
import com.sakura.client.gui.widget.SliderWidget;
import com.sakura.client.gui.widget.ToggleWidget;
import com.sakura.client.module.Module;
import com.sakura.client.render.LocalTransform;
import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.RenderUtils.Align;
import com.sakura.client.setting.BooleanSetting;
import com.sakura.client.setting.ChanceSetting;
import com.sakura.client.setting.ColorSetting;
import com.sakura.client.setting.EnumSetting;
import com.sakura.client.setting.MultiChoiceSetting;
import com.sakura.client.setting.NumberSetting;
import com.sakura.client.setting.RangeSetting;
import com.sakura.client.setting.Risk;
import com.sakura.client.setting.Setting;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Slide-out panel holding the parameters of one module.
 *
 * <p>Rendered by {@code ClickGuiScreen} in the menu's local coordinate space, so it inherits the window
 * transform and receives already-converted clicks. Every control is built from the setting's
 * {@link com.sakura.client.setting.SettingType}, which is what lets a new module expose parameters without
 * writing any UI code.</p>
 */
public final class ModuleSettingsPanel {

	// ------------------------------------------------------------------ palette
	private static final int RISK_SAFE = 0xFF62C46A;
	private static final int RISK_RISKY = 0xFFE0A44A;
	private static final int RISK_OUTDATED = 0xFFE05A5A;

	// ----------------------------------------------------------------- geometry
	public static final float WIDTH = 236.0f;
	private static final float HEADER_HEIGHT = 44.0f;
	private static final float PADDING = 12.0f;
	private static final float ROW_GAP = 9.0f;
	private static final float LABEL_LINE = 14.0f;
	private static final float CHIP_HEIGHT = 15.0f;
	private static final float CHIP_GAP = 4.0f;

	/** One laid-out parameter row, recomputed every frame. */
	private record Row(Setting<?> setting, float y, float height) {
	}

	/** A clickable filter chip belonging to a multi-choice setting. */
	private record Chip(Setting<?> setting, Enum<?> choice, Rect rect) {
	}

	private Module module;
	private float x;
	private float y;
	private float height;
	private float scroll;
	private float contentHeight;
	private float contentTop;

	private final Map<String, ToggleWidget> toggles = new HashMap<>();
	private final Map<String, SliderWidget> sliders = new HashMap<>();
	private final Map<String, DropdownWidget> dropdowns = new HashMap<>();
	private final Map<String, ColorPickerWidget> pickers = new HashMap<>();
	private final Map<String, Boolean> colorExpanded = new HashMap<>();

	private final List<Row> rows = new ArrayList<>();
	private final List<Chip> chips = new ArrayList<>();
	private Rect closeButton = new Rect(0.0f, 0.0f, 16.0f, 16.0f);
	private Rect resetButton = new Rect(0.0f, 0.0f, 0.0f, 0.0f);

	// -------------------------------------------------------------------- state

	public boolean isOpen() {
		return this.module != null;
	}

	public Module getModule() {
		return this.module;
	}

	public void open(Module target) {
		if (this.module != target) {
			// Widgets cache per-module state such as expansion, so they cannot be reused across modules.
			clearWidgets();
			this.scroll = 0.0f;
		}

		this.module = target;
	}

	public void close() {
		this.module = null;
		this.scroll = 0.0f;
	}

	private void clearWidgets() {
		this.toggles.clear();
		this.sliders.clear();
		this.dropdowns.clear();
		this.pickers.clear();
		this.colorExpanded.clear();
	}

	public Rect getBounds() {
		return new Rect(this.x, this.y, WIDTH, this.height);
	}

	public boolean contains(double localX, double localY) {
		return this.module != null
				&& localX >= this.x && localX < this.x + WIDTH
				&& localY >= this.y && localY < this.y + this.height;
	}

	// ------------------------------------------------------------------- layout

	/** Places the panel; called once per frame before {@link #render}. */
	public void layout(float panelX, float panelY, float panelHeight) {
		this.x = panelX;
		this.y = panelY;
		this.height = panelHeight;
		this.closeButton = new Rect(panelX + WIDTH - 22.0f, panelY + 8.0f, 16.0f, 16.0f);

		this.rows.clear();

		if (this.module == null) {
			return;
		}

		float cursor = 0.0f;
		List<Setting<?>> settings = this.module.getVisibleSettings();

		for (Setting<?> setting : settings) {
			float rowHeight = heightOf(setting);
			this.rows.add(new Row(setting, cursor, rowHeight));
			cursor += rowHeight + ROW_GAP;
		}

		this.resetButton = new Rect(this.x + PADDING, this.y + HEADER_HEIGHT + 8.0f + cursor - this.scroll,
				WIDTH - 2.0f * PADDING, 20.0f);
		this.contentHeight = cursor + 28.0f;
	}

	/** Height of a single parameter row, which depends on the widget state of that setting. */
	private float heightOf(Setting<?> setting) {
		return switch (setting.type()) {
			case BOOLEAN, NUMBER, CHANCE -> 20.0f;
			case RANGE -> 40.0f;
			case ENUM -> dropdown(setting).getHeight() + LABEL_LINE;
			case MULTI_CHOICE -> LABEL_LINE + chipsHeight(setting);
			case COLOR -> 20.0f + (isColorExpanded(setting)
					? ColorPickerWidget.widgetHeight() + 6.0f : 0.0f);
		};
	}

	private float chipsHeight(Setting<?> setting) {
		int count = ((MultiChoiceSetting<?>) setting).getChoices().size();
		int perLine = 3;
		int lines = (count + perLine - 1) / perLine;
		return lines * (CHIP_HEIGHT + CHIP_GAP);
	}

	private float maxScroll() {
		float viewport = this.height - HEADER_HEIGHT - 8.0f;
		return Math.max(0.0f, this.contentHeight - viewport);
	}

	// -------------------------------------------------------------------- render

	public void render(DrawContext context, float mouseX, float mouseY) {
		if (this.module == null) {
			return;
		}

		RenderUtils.drawRoundedRect(context, this.x, this.y, WIDTH, this.height, 10.0f, Theme.panelBg());
		RenderUtils.drawBorder(context, this.x, this.y, WIDTH, this.height, 10.0f, 1.0f, Theme.panelBorder());

		drawHeader(context, mouseX, mouseY);

		this.contentTop = this.y + HEADER_HEIGHT + 8.0f;

		// The scissor has to be expressed in screen space, hence the transform lookup.
		LocalTransform.enableScissor(context, this.x + 1.0f, this.contentTop,
				WIDTH - 2.0f, this.height - HEADER_HEIGHT - 10.0f);

		this.chips.clear();

		for (Row row : this.rows) {
			float rowY = this.contentTop + row.y() - this.scroll;

			// Skip rows fully outside the viewport; the clip handles the partially visible ones.
			if (rowY + row.height() < this.contentTop - 4.0f || rowY > this.y + this.height + 4.0f) {
				continue;
			}

			renderRow(context, row.setting(), rowY, mouseX, mouseY);
		}

		if (this.module.getVisibleSettings().isEmpty()) {
			RenderUtils.drawTextVCentered(context, "No parameters for this module yet.",
					this.x + PADDING, this.contentTop + 6.0f, 16.0f, Theme.textFaint(), false, Align.LEFT);
		}

		drawResetButton(context, mouseX, mouseY);

		context.disableScissor();

		drawScrollbar(context);
	}

	private void drawHeader(DrawContext context, float mouseX, float mouseY) {
		RenderUtils.drawRoundedRect(context, this.x + 1.0f, this.y + 1.0f, WIDTH - 2.0f, HEADER_HEIGHT - 1.0f,
				9.0f, Theme.headerBg());
		RenderUtils.drawTextVCentered(context, this.module.getName(), this.x + PADDING, this.y + 6.0f, 16.0f,
				Theme.text(), false, Align.LEFT);

		Risk risk = this.module.getRisk();

		if (risk != Risk.SAFE) {
			// The chip follows the active settings, so it changes as the mode changes.
			drawRiskChip(context, risk, this.x + PADDING + RenderUtils.textWidth(this.module.getName()) + 8.0f,
					this.y + 8.0f);
		}
		RenderUtils.drawTextVCentered(context,
				this.module.getCategory().getDisplayName() + "  -  "
						+ this.module.getVisibleSettings().size() + " parameters",
				this.x + PADDING, this.y + 22.0f, 12.0f, Theme.textDim(), false, Align.LEFT);

		boolean hovered = this.closeButton.contains(mouseX, mouseY);
		RenderUtils.drawTextVCentered(context, "\u2715", this.closeButton.x() + this.closeButton.width() / 2.0f,
				this.closeButton.y(), this.closeButton.height(), hovered ? Theme.text() : Theme.textDim(), false, Align.CENTER);
		RenderUtils.drawRect(context, this.x + 1.0f, this.y + HEADER_HEIGHT, WIDTH - 2.0f, 1.0f, Theme.headerDivider());
	}

	private void renderRow(DrawContext context, Setting<?> setting, float rowY, float mouseX, float mouseY) {
		switch (setting.type()) {
			case BOOLEAN -> {
				BooleanSetting booleanSetting = (BooleanSetting) setting;
				ToggleWidget toggle = toggle(booleanSetting);
				toggle.setOn(booleanSetting.get());
				RenderUtils.drawTextVCentered(context, setting.getName(), this.x + PADDING, rowY, 20.0f,
						Theme.text(), false, Align.LEFT);
				toggle.draw(context, this.x + WIDTH - PADDING - ToggleWidget.widgetWidth(),
						rowY + (20.0f - ToggleWidget.widgetHeight()) / 2.0f);
			}
			case NUMBER, CHANCE -> {
				SliderWidget slider = slider(setting, "value");
				float min = minimumOf(setting);
				float max = maximumOf(setting);

				if (!slider.isDragging()) {
					slider.setFromRange(valueOf(setting), min, max);
				}

				slider.draw(context, this.x + PADDING, rowY, WIDTH - 2.0f * PADDING,
						setting.getName(), setting.displayValue());
			}
			case RANGE -> {
				RangeSetting range = (RangeSetting) setting;
				SliderWidget lower = slider(setting, "lower");
				SliderWidget upper = slider(setting, "upper");

				float min = (float) range.getLowerBound();
				float max = (float) range.getUpperBound();

				if (!lower.isDragging()) {
					lower.setFromRange((float) range.getLower(), min, max);
				}

				if (!upper.isDragging()) {
					upper.setFromRange((float) range.getUpper(), min, max);
				}

				lower.draw(context, this.x + PADDING, rowY, WIDTH - 2.0f * PADDING, setting.getName(),
						formatNumber(range.getLower(), range.isIntegral()));
				upper.draw(context, this.x + PADDING, rowY + 20.0f, WIDTH - 2.0f * PADDING, null,
						formatNumber(range.getUpper(), range.isIntegral()));
			}
			case ENUM -> {
				EnumSetting<?> enumSetting = (EnumSetting<?>) setting;
				DropdownWidget dropdown = dropdown(setting);

				if (!dropdown.isExpanded()) {
					dropdown.setSelectedIndex(enumSetting.getValues().indexOf(currentEnum(enumSetting)));
				}

				dropdown.draw(context, this.x + PADDING, rowY, WIDTH - 2.0f * PADDING, mouseX, mouseY);
				drawRiskChip(context, enumSetting.getRisk(), this.x + PADDING,
						rowY + dropdown.getHeight() + 1.0f);
			}
			case MULTI_CHOICE -> renderMultiChoice(context, (MultiChoiceSetting<?>) setting, rowY);
			case COLOR -> renderColor(context, setting, rowY, mouseX, mouseY);
		}
	}

	private void renderMultiChoice(DrawContext context, MultiChoiceSetting<?> setting, float rowY) {
		RenderUtils.drawTextVCentered(context, setting.getName(), this.x + PADDING, rowY, LABEL_LINE,
				Theme.text(), false, Align.LEFT);

		float chipY = rowY + LABEL_LINE;
		float innerWidth = WIDTH - 2.0f * PADDING;
		float chipWidth = (innerWidth - 2.0f * CHIP_GAP) / 3.0f;
		int index = 0;

		for (Enum<?> choice : choicesOf(setting)) {
			int column = index % 3;
			int line = index / 3;
			float chipX = this.x + PADDING + column * (chipWidth + CHIP_GAP);
			float y = chipY + line * (CHIP_HEIGHT + CHIP_GAP);
			Rect rect = new Rect(chipX, y, chipWidth, CHIP_HEIGHT);
			boolean selected = containsChoice(setting, choice);

			this.chips.add(new Chip(setting, choice, rect));

			RenderUtils.drawRoundedRect(context, chipX, y, chipWidth, CHIP_HEIGHT, 4.0f,
					selected ? Theme.accentAlpha(0x40) : Theme.chipBg());
			RenderUtils.drawTextVCentered(context, labelOfChoice(setting, choice),
					chipX + chipWidth / 2.0f, y, CHIP_HEIGHT, selected ? Theme.text() : Theme.textDim(), false, Align.CENTER);
			index++;
		}
	}

	private void renderColor(DrawContext context, Setting<?> rawSetting, float rowY, float mouseX, float mouseY) {
		ColorSetting setting = asColor(rawSetting);
		RenderUtils.drawTextVCentered(context, setting.getName(), this.x + PADDING, rowY, 20.0f,
				Theme.text(), false, Align.LEFT);

		float swatchWidth = 70.0f;
		float swatchX = this.x + WIDTH - PADDING - swatchWidth;
		boolean expanded = isColorExpanded(setting);
		boolean hovered = mouseX >= swatchX && mouseX < swatchX + swatchWidth
				&& mouseY >= rowY && mouseY < rowY + 20.0f;

		RenderUtils.drawRoundedRect(context, swatchX, rowY + 2.0f, swatchWidth, 16.0f, 4.0f, setting.get());
		RenderUtils.drawBorder(context, swatchX, rowY + 2.0f, swatchWidth, 16.0f, 4.0f, 1.0f,
				hovered || expanded ? Theme.buttonBgHover() : Theme.buttonBg());
		RenderUtils.drawTextVCentered(context, setting.getHex(), swatchX + swatchWidth - 4.0f, rowY + 2.0f, 16.0f,
				Theme.text(), false, Align.RIGHT);

		if (expanded) {
			ColorPickerWidget picker = picker(setting);

			if (!picker.isDragging()) {
				picker.setColor(setting.get());
			}

			picker.draw(context, this.x + PADDING, rowY + 22.0f);
		}
	}

	private void drawRiskChip(DrawContext context, Risk risk, float x, float y) {
		String label = risk.getLabel();
		float width = RenderUtils.textWidth(label) + 12.0f;
		int color = colorOf(risk);

		RenderUtils.drawRoundedRect(context, x, y, width, 12.0f, 3.0f, (color & 0xFFFFFF) | 0x33000000);
		RenderUtils.drawTextVCentered(context, label, x + width / 2.0f, y, 12.0f, color, false, Align.CENTER);
	}

	private void drawResetButton(DrawContext context, float mouseX, float mouseY) {
		if (this.resetButton.width() <= 0.0f) {
			return;
		}

		boolean hovered = this.resetButton.contains(mouseX, mouseY);
		RenderUtils.drawRoundedRect(context, this.resetButton.x(), this.resetButton.y(),
				this.resetButton.width(), this.resetButton.height(), 5.0f,
				hovered ? Theme.buttonBgHover() : Theme.buttonBg());
		RenderUtils.drawTextVCentered(context, "\u21BA  Restore defaults",
				this.resetButton.x() + this.resetButton.width() / 2.0f, this.resetButton.y(),
				this.resetButton.height(), hovered ? Theme.text() : Theme.textDim(), false, Align.CENTER);
	}

	private void drawScrollbar(DrawContext context) {
		float maxScroll = maxScroll();

		if (maxScroll <= 0.0f) {
			return;
		}

		float viewport = this.height - HEADER_HEIGHT - 10.0f;
		float trackX = this.x + WIDTH - 5.0f;
		float thumbHeight = Math.max(20.0f, viewport * (viewport / this.contentHeight));
		float thumbY = this.contentTop + (viewport - thumbHeight) * (this.scroll / maxScroll);

		RenderUtils.drawRoundedRect(context, trackX, this.contentTop, 2.5f, viewport, 1.25f, Theme.buttonBg());
		RenderUtils.drawRoundedRect(context, trackX, thumbY, 2.5f, thumbHeight, 1.25f, Theme.scrollbar());
	}

	// --------------------------------------------------------------------- input

	public boolean mouseClicked(Click click) {
		if (this.module == null || !contains(click.x(), click.y())) {
			return false;
		}

		if (this.closeButton.contains(click.x(), click.y())) {
			close();
			return true;
		}

		if (this.resetButton.width() > 0.0f && this.resetButton.contains(click.x(), click.y())) {
			this.module.restoreSettings();
			return true;
		}

		for (Chip chip : this.chips) {
			if (chip.rect().contains(click.x(), click.y())) {
				toggleChoice(chip.setting(), chip.choice());
				return true;
			}
		}

		for (Row row : this.rows) {
			float rowY = this.contentTop + row.y() - this.scroll;
			Setting<?> setting = row.setting();

			switch (setting.type()) {
				case BOOLEAN -> {
					ToggleWidget toggle = toggle((BooleanSetting) setting);

					if (toggle.mouseClicked(click)) {
						((BooleanSetting) setting).set(toggle.isOn());
						return true;
					}
				}
				case NUMBER, CHANCE -> {
					SliderWidget slider = slider(setting, "value");

					if (slider.mouseClicked(click)) {
						return true;
					}
				}
				case RANGE -> {
					if (slider(setting, "lower").mouseClicked(click)) {
						return true;
					}

					if (slider(setting, "upper").mouseClicked(click)) {
						return true;
					}
				}
				case ENUM -> {
					DropdownWidget dropdown = dropdown(setting);

					if (dropdown.mouseClicked(click)) {
						return true;
					}
				}
				case COLOR -> {
					ColorSetting colorSetting = asColor(setting);

					if (isColorExpanded(colorSetting)) {
						if (picker(colorSetting).mouseClicked(click)) {
							colorSetting.set(picker(colorSetting).getColor());
							return true;
						}
					}

					if (colorSwatchContains(colorSetting, rowY, click.x(), click.y())) {
						this.colorExpanded.put(colorSetting.getName(), !isColorExpanded(colorSetting));
						return true;
					}
				}
				case MULTI_CHOICE -> {
				}
			}
		}

		// Empty panel space still swallows the click so it cannot reach the module list underneath.
		return true;
	}

	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (this.module == null) {
			return false;
		}

		for (Setting<?> setting : this.module.getSettings()) {
			boolean consumed = switch (setting.type()) {
				case NUMBER, CHANCE -> slider(setting, "value").mouseDragged(click, offsetX, offsetY);
				case RANGE -> slider(setting, "lower").mouseDragged(click, offsetX, offsetY)
						|| slider(setting, "upper").mouseDragged(click, offsetX, offsetY);
				case COLOR -> isColorExpanded(setting)
						&& picker(setting).mouseDragged(click, offsetX, offsetY);
				default -> false;
			};

			if (consumed) {
				pushToSettings();
				return true;
			}
		}

		return false;
	}

	public boolean mouseReleased(Click click) {
		if (this.module == null) {
			return false;
		}

		// Committed before the widgets are released: a slider reports its value only while it is dragging, so
		// releasing first would silently drop the value of a click that never turned into a drag.
		pushToSettings();

		boolean consumed = false;

		for (Setting<?> setting : this.module.getSettings()) {
			switch (setting.type()) {
				case NUMBER, CHANCE -> consumed |= slider(setting, "value").mouseReleased(click);
				case RANGE -> consumed |= slider(setting, "lower").mouseReleased(click)
						| slider(setting, "upper").mouseReleased(click);
				case COLOR -> {
					if (isColorExpanded(setting)) {
						consumed |= picker(setting).mouseReleased(click);
					}
				}
				default -> {
				}
			}
		}

		return consumed;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (this.module == null || !contains(mouseX, mouseY)) {
			return false;
		}

		float maxScroll = maxScroll();

		if (maxScroll <= 0.0f) {
			return true;
		}

		this.scroll = Math.clamp(this.scroll - (float) amount * 18.0f, 0.0f, maxScroll);
		return true;
	}

	// -------------------------------------------------------------- value syncing

	/**
	 * Copies widget state back into the settings.
	 *
	 * <p>Called after input rather than during render, so a setting listener that reads several settings at
	 * once never observes a half-applied frame.</p>
	 */
	private void pushToSettings() {
		if (this.module == null) {
			return;
		}

		for (Setting<?> setting : this.module.getSettings()) {
			switch (setting.type()) {
				case NUMBER -> {
					NumberSetting numberSetting = (NumberSetting) setting;
					SliderWidget slider = slider(setting, "value");

					if (slider.isDragging()) {
						numberSetting.setFraction(slider.getValue());
					}
				}
				case CHANCE -> {
					ChanceSetting chanceSetting = (ChanceSetting) setting;
					SliderWidget slider = slider(setting, "value");

					if (slider.isDragging()) {
						chanceSetting.set((double) slider.map(0.0f, 100.0f));
					}
				}
				case RANGE -> {
					RangeSetting range = (RangeSetting) setting;

					if (slider(setting, "lower").isDragging()) {
						range.setLower(slider(setting, "lower").map((float) range.getLowerBound(),
								(float) range.getUpperBound()));
					}

					if (slider(setting, "upper").isDragging()) {
						range.setUpper(slider(setting, "upper").map((float) range.getLowerBound(),
								(float) range.getUpperBound()));
					}
				}
				case ENUM -> applyEnumSelection((EnumSetting<?>) setting);
				case COLOR -> {
					ColorSetting colorSetting = asColor(setting);

					if (isColorExpanded(colorSetting) && picker(colorSetting).isDragging()) {
						colorSetting.set(picker(colorSetting).getColor());
					}
				}
				default -> {
				}
			}
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void applyEnumSelection(EnumSetting<?> setting) {
		DropdownWidget dropdown = dropdown(setting);
		int index = dropdown.getSelectedIndex();

		if (index < 0 || index >= setting.getValues().size()) {
			return;
		}

		Object selected = setting.getValues().get(index);

		if (selected != setting.get()) {
			((EnumSetting) setting).set((Enum) selected);
		}
	}

	// ------------------------------------------------------------------- widgets

	private ToggleWidget toggle(BooleanSetting setting) {
		ToggleWidget widget = this.toggles.computeIfAbsent(setting.getName(),
				name -> new ToggleWidget(setting.get()));
		widget.setOnToggle(() -> setting.set(widget.isOn()));
		return widget;
	}

	private SliderWidget slider(Setting<?> setting, String role) {
		return this.sliders.computeIfAbsent(setting.getName() + "#" + role, name -> new SliderWidget(0.0f));
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private DropdownWidget dropdown(Setting<?> setting) {
		return this.dropdowns.computeIfAbsent(setting.getName(), name -> {
			EnumSetting<?> enumSetting = (EnumSetting<?>) setting;
			List<String> labels = new ArrayList<>();

			for (Object value : enumSetting.getValues()) {
				labels.add(labelOfRaw(enumSetting, value));
			}

			DropdownWidget widget = new DropdownWidget("Mode", labels.toArray(new String[0]));
			widget.setSelectedIndex(enumSetting.getValues().indexOf(enumSetting.get()));
			return widget;
		});
	}

	private ColorPickerWidget picker(Setting<?> setting) {
		ColorPickerWidget widget = this.pickers.computeIfAbsent(setting.getName(),
				name -> new ColorPickerWidget());
		return widget;
	}

	private boolean isColorExpanded(Setting<?> setting) {
		return this.colorExpanded.getOrDefault(setting.getName(), false);
	}

	private boolean colorSwatchContains(ColorSetting setting, float rowY, double mouseX, double mouseY) {
		float swatchX = this.x + WIDTH - PADDING - 70.0f;
		return mouseX >= swatchX && mouseX < swatchX + 70.0f && mouseY >= rowY && mouseY < rowY + 20.0f;
	}

	// ------------------------------------------------------------ setting helpers

	private static float valueOf(Setting<?> setting) {
		return switch (setting.type()) {
			case NUMBER -> (float) ((NumberSetting) setting).get().doubleValue();
			case CHANCE -> (float) ((ChanceSetting) setting).get().doubleValue();
			default -> 0.0f;
		};
	}

	private static float minimumOf(Setting<?> setting) {
		return switch (setting.type()) {
			case NUMBER -> (float) ((NumberSetting) setting).getMin();
			case CHANCE -> 0.0f;
			default -> 0.0f;
		};
	}

	private static float maximumOf(Setting<?> setting) {
		return switch (setting.type()) {
			case NUMBER -> (float) ((NumberSetting) setting).getMax();
			case CHANCE -> 100.0f;
			default -> 0.0f;
		};
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Enum<?> currentEnum(EnumSetting<?> setting) {
		return (Enum<?>) setting.get();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static List<Enum<?>> choicesOf(MultiChoiceSetting<?> setting) {
		return (List) setting.getChoices();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static boolean containsChoice(MultiChoiceSetting<?> setting, Enum<?> choice) {
		return ((MultiChoiceSetting) setting).contains(choice);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static String labelOfChoice(MultiChoiceSetting<?> setting, Enum<?> choice) {
		return ((MultiChoiceSetting) setting).labelOf(choice);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void toggleChoice(Setting<?> setting, Enum<?> choice) {
		((MultiChoiceSetting) setting).toggle(choice);
	}

	/**
	 * Narrows a {@code Setting<?>} to its concrete class.
	 *
	 * <p>Goes through the raw type because {@code Setting<?>} holds a capture that Java will not narrow to a
	 * parameterised subclass directly.</p>
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static ColorSetting asColor(Setting<?> setting) {
		return (ColorSetting) (Setting) setting;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static String labelOfRaw(EnumSetting<?> setting, Object value) {
		return ((EnumSetting) setting).labelOf((Enum) value);
	}

	private static int colorOf(Risk risk) {
		return switch (risk) {
			case SAFE -> RISK_SAFE;
			case RISKY -> RISK_RISKY;
			case OUTDATED -> RISK_OUTDATED;
		};
	}

	private static String formatNumber(double number, boolean integral) {
		return integral ? String.valueOf((int) Math.round(number))
				: String.format(java.util.Locale.ROOT, "%.2f", number);
	}
}
