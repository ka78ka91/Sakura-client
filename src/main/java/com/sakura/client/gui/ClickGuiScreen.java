package com.sakura.client.gui;

import com.sakura.client.SakuraClient;
import com.sakura.client.config.ConfigManager;
import com.sakura.client.config.SakuraConfig;
import com.sakura.client.gui.widget.ColorPickerWidget;
import com.sakura.client.gui.widget.DropdownWidget;
import com.sakura.client.gui.widget.GuiWidget;
import com.sakura.client.gui.widget.KeybindWidget;
import com.sakura.client.gui.widget.SliderWidget;
import com.sakura.client.gui.widget.ToggleWidget;
import com.sakura.client.hud.HudManager;
import com.sakura.client.hud.HudModule;
import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import com.sakura.client.module.ModuleManager;
import com.sakura.client.notification.NotificationManager;
import com.sakura.client.render.LocalTransform;
import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.RenderUtils.Align;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sakura Client main menu: a 900x550 glass window with a 180px sidebar and a routed content area.
 *
 * <p>Everything is laid out in <em>local window space</em> —{@code (0,0)} is the window's own top-left
 * corner. The whole window is then painted inside one matrix transform scaled by the "GUI scale"
 * setting, and incoming mouse coordinates are converted back into that space before hit testing. That
 * keeps every hit box independent of the scale factor.</p>
 *
 * <p>Pages: one per {@link Category} (module toggles + key binds), Settings (the prototype's
 * Appearance/Window sections), Config (file + HUD positions) and HUD Editor.</p>
 */
public class ClickGuiScreen extends Screen {

	// ------------------------------------------------------------------ palette
	private static final int WINDOW_BG = 0xE0141414;
	private static final int WINDOW_BORDER = 0x1AFFFFFF;
	private static final int SIDEBAR_BG = 0x800F0F0F;
	private static final int SIDEBAR_DIVIDER = 0x14FFFFFF;
	private static final int ROW_SELECTED = 0x20FFFFFF;
	private static final int ROW_HOVER = 0x15FFFFFF;
	private static final int ROW_TEXT = 0xFFCCCCCC;
	private static final int ROW_ICON = 0xB3FFFFFF;
	private static final int SECTION_BG = 0x33000000;
	private static final int SECTION_OUTLINE = 0x08FFFFFF;
	private static final int HEADER_DIVIDER = 0x0DFFFFFF;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFAAAAAA;
	private static final int TEXT_FAINT = 0x80FFFFFF;
	private static final int ACCENT_TEXT = 0xFFA06EFF;
	private static final int USER_CARD_BG = 0x4D000000;
	private static final int USER_AVATAR = 0xFFD97D54;
	private static final int USER_TAG_BG = 0x33FF6B6B;
	private static final int USER_TAG_TEXT = 0xFFFF6B6B;
	private static final int BUTTON_BG = 0x1AFFFFFF;
	private static final int BUTTON_BG_HOVER = 0x26FFFFFF;
	private static final int SCROLLBAR = 0x33FFFFFF;

	// ----------------------------------------------------------------- geometry
	public static final int WINDOW_WIDTH = 900;
	public static final int WINDOW_HEIGHT = 550;
	public static final int SIDEBAR_WIDTH = 180;

	private static final float WINDOW_RADIUS = 12.0f;
	private static final float SIDEBAR_PADDING = 10.0f;
	private static final float MENU_ROW_HEIGHT = 22.0f;
	private static final float MENU_ROW_GAP = 2.0f;
	private static final float GROUP_LABEL_HEIGHT = 16.0f;
	private static final float GROUP_GAP = 14.0f;
	private static final float LOGO_TOP = 18.0f;
	private static final float LOGO_BLOCK_HEIGHT = 40.0f;
	private static final float MENU_ICON_COLUMN = 26.0f;
	private static final float USER_CARD_HEIGHT = 46.0f;
	private static final float CONTENT_PADDING_X = 26.0f;
	private static final float HEADER_HEIGHT = 78.0f;
	private static final float SECTION_HEADER_HEIGHT = 34.0f;
	private static final float SECTION_GAP = 16.0f;
	private static final float SECTION_PADDING = 12.0f;
	/**
	 * Left inset of a section title inside its box. The page title is drawn at the same visual inset —see the
	 * title block in {@code drawContent}.
	 */
	private static final float SECTION_TITLE_INSET = 14.0f;
	/** Gap kept between the settings panel and the window edge, so the body scrollbar stays visible. */
	private static final float PANEL_RIGHT_MARGIN = 18.0f;
	private static final float ROW_HEIGHT = 24.0f;
	private static final float MODULE_ROW_HEIGHT = 28.0f;
	private static final float BUTTON_HEIGHT = 20.0f;
	private static final float SCROLL_STEP = 24.0f;

	private static final float MIN_GUI_SCALE = 0.5f;
	private static final float MAX_GUI_SCALE = 1.5f;
	private static final float MAX_HUD_RADIUS = 50.0f;

	/** Sidebar entries that are not module categories. */
	private enum UtilityPage {
		SETTINGS("Settings", "Personalization", "\u2699"),
		CONFIG("Config", "Files and persistence", "\u25A3"),
		HUD_EDITOR("HUD Editor", "Drag and place HUD elements", "\u25A6");

		private final String title;
		private final String subtitle;
		private final String icon;

		UtilityPage(String title, String subtitle, String icon) {
			this.title = title;
			this.subtitle = subtitle;
			this.icon = icon;
		}
	}

	private record Target(Category category, UtilityPage page) {
		static Target of(Category category) {
			return new Target(category, null);
		}

		static Target of(UtilityPage page) {
			return new Target(null, page);
		}

		String title() {
			return this.category != null ? this.category.getDisplayName() : this.page.title;
		}

		String subtitle() {
			return this.category != null ? "Modules" : this.page.subtitle;
		}
	}

	private record Entry(String icon, String label, Target target) {
	}

	/** Axis-aligned hit box in local window space. */
	public record Rect(float x, float y, float width, float height) {
		public boolean contains(double mouseX, double mouseY) {
			return mouseX >= this.x && mouseX < this.x + this.width
					&& mouseY >= this.y && mouseY < this.y + this.height;
		}

		public float right() {
			return this.x + this.width;
		}

		public float bottom() {
			return this.y + this.height;
		}
	}

	private record Row(Entry entry, Rect rect) {
	}

	private record GroupLabel(String text, Rect rect) {
	}

	private record Button(Rect rect, Runnable action) {
	}

	private static final Map<Category, String> CATEGORY_ICONS = Map.of(
			Category.COMBAT, "\u2694",
			Category.MOVEMENT, "\u27A4",
			Category.PLAYER, "\u263B",
			Category.VISUALS, "\u25C9",
			Category.HUD, "\u25A4",
			Category.WORLD, "\u2295",
			Category.MISC, "\u2699");

	// -------------------------------------------------------------------- state
	private final List<Entry> features = new ArrayList<>();
	private final List<Entry> others = new ArrayList<>();
	private final List<Row> rows = new ArrayList<>();
	private final List<GroupLabel> groupLabels = new ArrayList<>();
	private final List<GuiWidget> activeWidgets = new ArrayList<>();
	private final List<Button> activeButtons = new ArrayList<>();
	private final Map<String, Rect> sectionHeaders = new LinkedHashMap<>();
	private final Map<String, Boolean> collapsedSections = new HashMap<>();
	private final Map<String, ToggleWidget> toggles = new HashMap<>();
	private final Map<String, KeybindWidget> keybinds = new HashMap<>();

	/**
	 * Hit boxes of the module rows drawn by the category pages, keyed by module name.
	 *
	 * <p>Filled during render and read during input, mirroring how {@code activeWidgets} works: widgets record
	 * their bounds while drawing because the render pass always runs before the input pass.</p>
	 */
	private final Map<String, Rect> moduleRows = new LinkedHashMap<>();
	private final ModuleSettingsPanel settingsPanel = new ModuleSettingsPanel();

	private final ColorPickerWidget accentPicker = new ColorPickerWidget();
	private final ToggleWidget descriptionsToggle = new ToggleWidget(true);
	private final SliderWidget hudRadiusSlider = new SliderWidget(0.32f);
	private final SliderWidget guiScaleSlider = new SliderWidget(0.5f);
	private final DropdownWidget moduleSettingsDropdown =
			new DropdownWidget("Module settings", "Side panel", "Popup", "Tooltip");

	private Target selected = Target.of(UtilityPage.SETTINGS);
	private Row hoveredRow;
	private Rect searchButton = new Rect(0.0f, 0.0f, 0.0f, 0.0f);
	private Rect closeButton = new Rect(0.0f, 0.0f, 0.0f, 0.0f);
	private float scroll;
	private float contentHeight;
	private float scale = 1.0f;
	private float originX;
	private float originY;
	private boolean scaleLimited;
	private boolean searching;
	private String searchQuery = "";

	public ClickGuiScreen() {
		super(Text.literal("Sakura Client"));

		for (Category category : Category.values()) {
			this.features.add(new Entry(CATEGORY_ICONS.getOrDefault(category, "\u2022"),
					category.getDisplayName(), Target.of(category)));
		}

		for (UtilityPage page : UtilityPage.values()) {
			this.others.add(new Entry(page.icon, page.title, Target.of(page)));
		}
	}

	/** The client keeps running while this menu is open. */
	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	protected void init() {
		loadFromConfig();
	}

	// --------------------------------------------------------------- config glue

	private void loadFromConfig() {
		SakuraConfig config = ConfigManager.get();

		this.accentPicker.setColor(config.accentColor);
		this.descriptionsToggle.setOn(config.descriptions);
		this.hudRadiusSlider.setFromRange(config.hudCornerRadius, 0.0f, MAX_HUD_RADIUS);
		this.guiScaleSlider.setFromRange(config.guiScale, MIN_GUI_SCALE, MAX_GUI_SCALE);
		this.moduleSettingsDropdown.setSelectedIndex(indexOfPanel(config.moduleSettingsPanel));
		this.selected = resolvePage(config.lastGuiPage);
	}

	private int indexOfPanel(String panel) {
		String[] options = {"Side panel", "Popup", "Tooltip"};

		for (int i = 0; i < options.length; i++) {
			if (options[i].equals(panel)) {
				return i;
			}
		}

		return 0;
	}

	/**
	 * Turns the stored page name back into the sidebar target it names.
	 *
	 * <p>Pages persist as enum names, so reordering the sidebar cannot silently redirect them; a name that
	 * no longer resolves —a removed category, a hand-edited file —falls back to Settings.</p>
	 */
	private static Target resolvePage(String stored) {
		if (stored != null && !stored.isBlank()) {
			for (Category category : Category.values()) {
				if (category.name().equals(stored)) {
					return Target.of(category);
				}
			}

			for (UtilityPage page : UtilityPage.values()) {
				if (page.name().equals(stored)) {
					return Target.of(page);
				}
			}
		}

		return Target.of(UtilityPage.SETTINGS);
	}

	/** Copies widget state back into the config and writes it to disk. */
	private void persistSettings() {
		SakuraConfig config = ConfigManager.get();

		config.accentColor = this.accentPicker.getColor();
		config.descriptions = this.descriptionsToggle.isOn();
		config.hudCornerRadius = this.hudRadiusSlider.map(0.0f, MAX_HUD_RADIUS);
		config.guiScale = this.guiScaleSlider.map(MIN_GUI_SCALE, MAX_GUI_SCALE);
		config.moduleSettingsPanel = this.moduleSettingsDropdown.getValue();

		ConfigManager.save();
	}

	private void reloadConfig() {
		ConfigManager.load();
		ModuleManager.applyPersistedState(ConfigManager.get().moduleStates, ConfigManager.get().moduleKeybinds);
		HudManager.loadPositions();
		loadFromConfig();
	}

	private void resetConfig() {
		ConfigManager.reset();
		ModuleManager.applyPersistedState(ConfigManager.get().moduleStates, ConfigManager.get().moduleKeybinds);
		HudManager.loadPositions();
		loadFromConfig();
	}

	/**
	 * Switches the active profile and re-applies everything the config drives, mirroring
	 * {@link #reloadConfig()}: module state, module settings, HUD positions and the widget values.
	 */
	private void switchToProfile(String profile) {
		// Persist the current edits first, so the snapshot switchProfile takes of the outgoing profile
		// includes them rather than whatever the last save happened to catch.
		persistSettings();

		if (!ConfigManager.switchProfile(profile)) {
			return;
		}

		SakuraConfig config = ConfigManager.get();
		ModuleManager.applyPersistedState(config.moduleStates, config.moduleKeybinds);
		SakuraClient.applyModuleSettings();
		HudManager.loadPositions();
		loadFromConfig();
	}

	@Override
	public void close() {
		// Remember the page the menu was on, so reopening it lands where the player left instead of always
		// falling back to Settings.
		SakuraConfig config = ConfigManager.get();
		config.lastGuiPage = this.selected.category() != null
				? this.selected.category().name()
				: this.selected.page().name();
		persistSettings();
		super.close();
	}

	// ------------------------------------------------------------------- layout

	/** Recomputes the window origin, the scale and every sidebar hit box. */
	private void layout() {
		float configured = Math.max(MIN_GUI_SCALE, Math.min(MAX_GUI_SCALE, ConfigManager.get().guiScale));

		// The window is a fixed 900x550 design. At high vanilla GUI scales the scaled screen can be
		// smaller than that, so the applied scale is additionally capped by what actually fits —		// otherwise the sidebar and the close button would sit off-screen.
		float fitting = Math.min(this.width / (float) WINDOW_WIDTH, this.height / (float) WINDOW_HEIGHT);
		this.scale = Math.max(0.1f, Math.min(configured, fitting));
		this.scaleLimited = this.scale < configured - 0.001f;

		this.originX = Math.round((this.width - WINDOW_WIDTH * this.scale) / 2.0f);
		this.originY = Math.round((this.height - WINDOW_HEIGHT * this.scale) / 2.0f);

		this.rows.clear();
		this.groupLabels.clear();
		this.hoveredRow = null;

		float rowX = SIDEBAR_PADDING;
		float rowWidth = SIDEBAR_WIDTH - 2.0f * SIDEBAR_PADDING;
		float cursorY = LOGO_TOP + LOGO_BLOCK_HEIGHT + 10.0f;
		cursorY = layoutGroup("FEATURES", this.features, rowX, cursorY, rowWidth);
		layoutGroup("OTHER", this.others, rowX, cursorY + GROUP_GAP, rowWidth);

		float actionsRight = WINDOW_WIDTH - 28.0f;
		this.closeButton = new Rect(actionsRight - 16.0f, 26.0f, 16.0f, 16.0f);
		this.searchButton = new Rect(actionsRight - 54.0f, 26.0f, 16.0f, 16.0f);
	}

	private float layoutGroup(String title, List<Entry> entries, float x, float y, float width) {
		this.groupLabels.add(new GroupLabel(title, new Rect(x + 10.0f, y, width, GROUP_LABEL_HEIGHT)));
		float rowY = y + GROUP_LABEL_HEIGHT;

		for (Entry entry : entries) {
			this.rows.add(new Row(entry, new Rect(x, rowY, width, MENU_ROW_HEIGHT)));
			rowY += MENU_ROW_HEIGHT + MENU_ROW_GAP;
		}

		return rowY;
	}

	private float contentX() {
		return SIDEBAR_WIDTH + CONTENT_PADDING_X;
	}

	private float contentWidth() {
		return WINDOW_WIDTH - SIDEBAR_WIDTH - 2.0f * CONTENT_PADDING_X;
	}

	private float bodyTop() {
		return HEADER_HEIGHT + 14.0f;
	}

	private float bodyBottom() {
		return WINDOW_HEIGHT - 12.0f;
	}

	private float maxScroll() {
		return Math.max(0.0f, this.contentHeight - (bodyBottom() - bodyTop()));
	}

	public float toLocalX(double screenX) {
		return (float) ((screenX - this.originX) / this.scale);
	}

	public float toLocalY(double screenY) {
		return (float) ((screenY - this.originY) / this.scale);
	}

	/** Inverse of {@link #toLocalX(double)}: local window space to screen pixels. */
	public float toScreenX(float localX) {
		return this.originX + localX * this.scale;
	}

	/** Inverse of {@link #toLocalY(double)}: local window space to screen pixels. */
	public float toScreenY(float localY) {
		return this.originY + localY * this.scale;
	}

	/** Scale actually applied this frame, which may be below the configured one to fit the screen. */
	public float getAppliedScale() {
		return this.scale;
	}

	/** Whether the applied scale had to be reduced below the configured value to fit. */
	public boolean isScaleLimited() {
		return this.scaleLimited;
	}

	/** Title of the page currently on screen; used by the automated render smoke test. */
	public String getPageTitle() {
		return this.searching ? "Search" : this.selected.title();
	}

	// ------------------------------------------------------------------- render

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// NOTE: in 1.21.11 Screen.renderWithTooltip already calls renderBackground for us, and the
		// blur post effect may only be applied once per frame —calling renderBackground here would
		// throw "Can only blur once per frame". The blur and darkening therefore come from vanilla.
		super.render(context, mouseX, mouseY, delta);

		layout();

		float localMouseX = toLocalX(mouseX);
		float localMouseY = toLocalY(mouseY);

		updateHover(localMouseX, localMouseY);

		this.activeWidgets.clear();
		this.activeButtons.clear();
		this.sectionHeaders.clear();
		this.moduleRows.clear();

		// Widgets clip in screen space but only know their local bounds, so they need this mapping.
		LocalTransform.set(this.originX, this.originY, this.scale);

		context.getMatrices().pushMatrix();
		context.getMatrices().translate(this.originX, this.originY);
		context.getMatrices().scale(this.scale, this.scale);

		RenderUtils.drawBlurredRect(context, 0.0f, 0.0f, WINDOW_WIDTH, WINDOW_HEIGHT, WINDOW_RADIUS, WINDOW_BG);
		drawSidebarSurface(context);
		drawSidebarContents(context);
		drawContent(context, localMouseX, localMouseY);
		RenderUtils.drawBorder(context, 0.0f, 0.0f, WINDOW_WIDTH, WINDOW_HEIGHT, WINDOW_RADIUS, 1.0f, WINDOW_BORDER);

		context.getMatrices().popMatrix();

		// Whatever draws after this point (the HUD) is in screen space again.
		LocalTransform.reset();
	}

	private void drawSidebarSurface(DrawContext context) {
		RenderUtils.drawRoundedRect(context, 0.0f, 0.0f, SIDEBAR_WIDTH, WINDOW_HEIGHT, WINDOW_RADIUS, SIDEBAR_BG);
		// Square off the two right corners so the sidebar meets the content edge as a straight line.
		RenderUtils.drawRect(context, SIDEBAR_WIDTH - WINDOW_RADIUS, 0.0f, WINDOW_RADIUS, WINDOW_RADIUS, SIDEBAR_BG);
		RenderUtils.drawRect(context, SIDEBAR_WIDTH - WINDOW_RADIUS, WINDOW_HEIGHT - WINDOW_RADIUS,
				WINDOW_RADIUS, WINDOW_RADIUS, SIDEBAR_BG);
		RenderUtils.drawRect(context, SIDEBAR_WIDTH - 1.0f, 0.0f, 1.0f, WINDOW_HEIGHT, SIDEBAR_DIVIDER);
	}

	private void drawSidebarContents(DrawContext context) {
		drawLogo(context);

		for (GroupLabel label : this.groupLabels) {
			RenderUtils.drawText(context, label.text(), label.rect().x(), label.rect().y() + 2.0f, TEXT_DIM, false);
		}

		int accent = ConfigManager.get().accentColor;

		for (Row row : this.rows) {
			boolean isSelected = row.entry().target().equals(this.selected) && !this.searching;
			boolean isHovered = row == this.hoveredRow;
			Rect rect = row.rect();

			if (isSelected) {
				RenderUtils.drawRoundedRect(context, rect.x(), rect.y(), rect.width(), rect.height(), 6.0f, ROW_SELECTED);
			} else if (isHovered) {
				RenderUtils.drawRoundedRect(context, rect.x(), rect.y(), rect.width(), rect.height(), 6.0f, ROW_HOVER);
			}

			int textColor = (isSelected || isHovered) ? TEXT : ROW_TEXT;
			int iconColor = isSelected ? accent : ROW_ICON;

			RenderUtils.drawTextVCentered(context, row.entry().icon(), rect.x() + 14.0f, rect.y(), rect.height(),
					iconColor, false, Align.CENTER);
			RenderUtils.drawTextVCentered(context, row.entry().label(), rect.x() + MENU_ICON_COLUMN, rect.y(),
					rect.height(), textColor, false, Align.LEFT);
		}

		drawUserCard(context);
	}

	private void drawLogo(DrawContext context) {
		RenderUtils.drawText(context, "\u2726", 18.0f, LOGO_TOP, ConfigManager.get().accentColor, true);

		context.getMatrices().pushMatrix();
		context.getMatrices().translate(34.0f, LOGO_TOP);
		context.getMatrices().scale(1.15f, 1.15f);
		RenderUtils.drawText(context, "Sakura Client", 0.0f, 0.0f, TEXT, true);
		context.getMatrices().popMatrix();

		RenderUtils.drawText(context, "1.21.11", 34.0f, LOGO_TOP + 12.0f, TEXT_DIM, false);
	}

	private void drawUserCard(DrawContext context) {
		float cardX = SIDEBAR_PADDING;
		float cardWidth = SIDEBAR_WIDTH - 2.0f * SIDEBAR_PADDING;
		float cardY = WINDOW_HEIGHT - SIDEBAR_PADDING - USER_CARD_HEIGHT;

		RenderUtils.drawRoundedRect(context, cardX, cardY, cardWidth, USER_CARD_HEIGHT, 8.0f, USER_CARD_BG);

		float avatarSize = 30.0f;
		float avatarX = cardX + 8.0f;
		float avatarY = cardY + (USER_CARD_HEIGHT - avatarSize) / 2.0f;
		RenderUtils.drawRoundedRect(context, avatarX, avatarY, avatarSize, avatarSize, 6.0f, USER_AVATAR);
		RenderUtils.drawTextVCentered(context, "\u263B", avatarX + avatarSize / 2.0f, avatarY, avatarSize,
				TEXT, false, Align.CENTER);

		float textX = avatarX + avatarSize + 8.0f;
		RenderUtils.drawText(context, "Idontkonw", textX, cardY + 9.0f, TEXT, false);

		String tag = "User";
		float tagWidth = RenderUtils.textWidth(tag) + 10.0f;
		float tagHeight = 12.0f;
		float tagY = cardY + 23.0f;
		RenderUtils.drawRoundedRect(context, textX, tagY, tagWidth, tagHeight, 4.0f, USER_TAG_BG);
		RenderUtils.drawTextVCentered(context, tag, textX + tagWidth / 2.0f, tagY, tagHeight,
				USER_TAG_TEXT, false, Align.CENTER);
	}

	private void drawContent(DrawContext context, float mouseX, float mouseY) {
		float contentX = contentX();
		float titleY = 20.0f;

		String title = this.searching ? "Search" : this.selected.title();
		String subtitle = this.searching
				? (this.searchQuery.isEmpty() ? "Type to filter modules" : this.searchQuery)
				: this.selected.subtitle();

		// The title is drawn at 1.6x. translate() runs before scale(), so this offset is in local units:
		// shifting the origin by the section inset lines the title's left edge up with the section headers
		// below it instead of leaving it outdented at the content edge. At 1.6x the glyph bearing adds about
		// 0.6px of extra inset, which is deliberately ignored.
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(contentX + SECTION_TITLE_INSET, titleY);
		context.getMatrices().scale(1.6f, 1.6f);
		RenderUtils.drawText(context, title, 0.0f, 0.0f, TEXT, true);
		context.getMatrices().popMatrix();

		RenderUtils.drawText(context, subtitle, contentX, titleY + 22.0f, TEXT_DIM, false);

		boolean searchHovered = this.searchButton.contains(mouseX, mouseY);
		boolean closeHovered = this.closeButton.contains(mouseX, mouseY);
		RenderUtils.drawTextVCentered(context, "\u2315", this.searchButton.x() + this.searchButton.width() / 2.0f,
				this.searchButton.y(), this.searchButton.height(),
				this.searching ? ACCENT_TEXT : (searchHovered ? TEXT : TEXT_DIM), false, Align.CENTER);
		RenderUtils.drawTextVCentered(context, "\u2715", this.closeButton.x() + this.closeButton.width() / 2.0f,
				this.closeButton.y(), this.closeButton.height(), closeHovered ? TEXT : TEXT_DIM, false, Align.CENTER);

		RenderUtils.drawRect(context, SIDEBAR_WIDTH, HEADER_HEIGHT, WINDOW_WIDTH - SIDEBAR_WIDTH, 1.0f,
				HEADER_DIVIDER);

		// Screen-space clip rectangle for the scrolling body.
		context.enableScissor(
				Math.round(this.originX + (SIDEBAR_WIDTH + 1.0f) * this.scale),
				Math.round(this.originY + (HEADER_HEIGHT + 1.0f) * this.scale),
				Math.round(this.originX + (WINDOW_WIDTH - 1.0f) * this.scale),
				Math.round(this.originY + (WINDOW_HEIGHT - 1.0f) * this.scale));

		float startY = bodyTop() - this.scroll;
		float endY = renderBody(context, mouseX, mouseY, startY);
		this.contentHeight = endY - bodyTop() + this.scroll + 8.0f;

		context.disableScissor();

		drawScrollbar(context);
		drawSettingsPanel(context, mouseX, mouseY);
		drawNotifications(context);
	}

	private void drawScrollbar(DrawContext context) {
		float maxScroll = maxScroll();

		if (maxScroll <= 0.0f) {
			return;
		}

		float viewport = bodyBottom() - bodyTop();
		float trackX = WINDOW_WIDTH - 12.0f;
		float thumbHeight = Math.max(24.0f, viewport * (viewport / this.contentHeight));
		float thumbY = bodyTop() + (viewport - thumbHeight) * (this.scroll / maxScroll);

		RenderUtils.drawRoundedRect(context, trackX, bodyTop(), 3.0f, viewport, 1.5f, 0x1AFFFFFF);
		RenderUtils.drawRoundedRect(context, trackX, thumbY, 3.0f, thumbHeight, 1.5f, SCROLLBAR);
	}

	/**
	 * Draws the module settings panel on top of the page body.
	 *
	 * <p>Deliberately called after the body scissor is released: the panel spans the full body height and clips
	 * its own scrolling content, which would otherwise be cut short by the body's clip rectangle.</p>
	 */
	private void drawSettingsPanel(DrawContext context, float mouseX, float mouseY) {
		if (!this.settingsPanel.isOpen()) {
			return;
		}

		float panelX = WINDOW_WIDTH - PANEL_RIGHT_MARGIN - ModuleSettingsPanel.WIDTH;
		float panelY = HEADER_HEIGHT + 12.0f;
		this.settingsPanel.layout(panelX, panelY, Math.max(120.0f, bodyBottom() - panelY - 12.0f));
		this.settingsPanel.render(context, mouseX, mouseY);
	}

	private void drawNotifications(DrawContext context) {
		if (NotificationManager.isEmpty()) {
			return;
		}

		// The window is scaled, so the inverse scale keeps notices the same apparent size as in the HUD.
		NotificationManager.render(context, WINDOW_WIDTH - 16.0f, HEADER_HEIGHT + 8.0f, 1.0f / this.scale);
	}

	// --------------------------------------------------------------- page router

	private float renderBody(DrawContext context, float mouseX, float mouseY, float y) {
		if (this.searching) {
			return renderSearchResults(context, mouseX, mouseY, y);
		}

		if (this.selected.category() != null) {
			return renderCategoryPage(context, mouseX, mouseY, y, this.selected.category());
		}

		return switch (this.selected.page()) {
			case SETTINGS -> renderSettingsPage(context, mouseX, mouseY, y);
			case CONFIG -> renderConfigPage(context, mouseX, mouseY, y);
			case HUD_EDITOR -> renderHudEditorPage(context, mouseX, mouseY, y);
		};
	}

	// -------------------------------------------------------------- settings page

	private float renderSettingsPage(DrawContext context, float mouseX, float mouseY, float y) {
		float width = contentWidth();

		// ---- Appearance -------------------------------------------------------
		float appearanceBody = SECTION_PADDING
				+ 18.0f
				+ ColorPickerWidget.widgetHeight()
				+ 8.0f
				+ ROW_HEIGHT
				+ ROW_HEIGHT
				+ this.moduleSettingsDropdown.getHeight()
				+ SECTION_PADDING;
		Rect appearance = drawSection(context, "Appearance", y, width, appearanceBody);

		if (!isCollapsed("Appearance")) {
			float rowX = appearance.x() + SECTION_PADDING;
			float rowWidth = appearance.width() - SECTION_PADDING * 2.0f;
			float rowY = appearance.y() + SECTION_HEADER_HEIGHT + SECTION_PADDING;

			RenderUtils.drawText(context, "Accent", rowX, rowY, TEXT, false);
			rowY += 18.0f;

			this.accentPicker.draw(context, rowX, rowY);
			this.activeWidgets.add(this.accentPicker);
			rowY += ColorPickerWidget.widgetHeight() + 8.0f;

			rowY = toggleRow(context, "Descriptions", "Show module descriptions", this.descriptionsToggle,
					rowX, rowY, rowWidth);
			rowY = sliderRow(context, "HUD corner radius", this.hudRadiusSlider,
					Math.round(this.hudRadiusSlider.map(0.0f, MAX_HUD_RADIUS)) + " px", rowX, rowY, rowWidth);
			dropdownRow(context, this.moduleSettingsDropdown, rowX, rowY, rowWidth, mouseX, mouseY);
		}

		y = appearance.bottom() + SECTION_GAP;

		// ---- Window -----------------------------------------------------------
		float windowBody = SECTION_PADDING + ROW_HEIGHT + SECTION_PADDING;
		Rect window = drawSection(context, "Window", y, width, windowBody);

		if (!isCollapsed("Window")) {
			float rowX = window.x() + SECTION_PADDING;
			float rowWidth = window.width() - SECTION_PADDING * 2.0f;
			float rowY = window.y() + SECTION_HEADER_HEIGHT + SECTION_PADDING;

			String scaleText = Math.round(this.guiScaleSlider.map(MIN_GUI_SCALE, MAX_GUI_SCALE) * 100.0f) + "%";

			if (this.scaleLimited) {
				scaleText += " \u00B7 fitted to " + Math.round(this.scale * 100.0f) + "%";
			}

			sliderRow(context, "GUI scale", this.guiScaleSlider, scaleText, rowX, rowY, rowWidth);
		}

		return window.bottom() + SECTION_GAP;
	}

	// ----------------------------------------------------------- category pages

	private float renderCategoryPage(DrawContext context, float mouseX, float mouseY, float y, Category category) {
		List<Module> modules = ModuleManager.getByCategory(category);
		String sectionTitle = category.getDisplayName() + " modules";
		float width = contentWidth();

		int rowCount = Math.max(1, modules.size());
		float body = SECTION_PADDING + rowCount * MODULE_ROW_HEIGHT + SECTION_PADDING;
		Rect box = drawSection(context, sectionTitle, y, width, body);

		if (!isCollapsed(sectionTitle)) {
			float rowX = box.x() + SECTION_PADDING;
			float rowWidth = box.width() - SECTION_PADDING * 2.0f;
			float rowY = box.y() + SECTION_HEADER_HEIGHT + SECTION_PADDING;

			if (modules.isEmpty()) {
				RenderUtils.drawTextVCentered(context, "No modules registered in this category yet.",
						rowX, rowY, MODULE_ROW_HEIGHT, TEXT_FAINT, false, Align.LEFT);
			}

			for (Module module : modules) {
				moduleRow(context, module, rowX, rowY, rowWidth, mouseX, mouseY);
				rowY += MODULE_ROW_HEIGHT;
			}
		}

		return box.bottom() + SECTION_GAP;
	}

	private float renderSearchResults(DrawContext context, float mouseX, float mouseY, float y) {
		List<Module> results = ModuleManager.search(this.searchQuery);
		float width = contentWidth();

		int rowCount = Math.max(1, results.size());
		float body = SECTION_PADDING + rowCount * MODULE_ROW_HEIGHT + SECTION_PADDING;
		Rect box = drawSection(context, "Results (" + results.size() + ")", y, width, body);

		float rowX = box.x() + SECTION_PADDING;
		float rowWidth = box.width() - SECTION_PADDING * 2.0f;
		float rowY = box.y() + SECTION_HEADER_HEIGHT + SECTION_PADDING;

		if (results.isEmpty()) {
			RenderUtils.drawTextVCentered(context, "No module matches \"" + this.searchQuery + "\".",
					rowX, rowY, MODULE_ROW_HEIGHT, TEXT_FAINT, false, Align.LEFT);
		}

		for (Module module : results) {
			moduleRow(context, module, rowX, rowY, rowWidth, mouseX, mouseY);
			rowY += MODULE_ROW_HEIGHT;
		}

		return box.bottom() + SECTION_GAP;
	}

	/**
	 * One module row: name + description, a key bind button and the enable switch. The switch is the
	 * source of truth for the module's own state, mirrored back into the config on every flip.
	 */
	private float moduleRow(DrawContext context, Module module, float x, float y, float width,
							 float mouseX, float mouseY) {
		// Recorded so a click on the row —but not on its toggle or key bind —can open the settings panel.
		this.moduleRows.put(module.getName(), new Rect(x, y, width, MODULE_ROW_HEIGHT));
		RenderUtils.drawTextVCentered(context, module.getName(), x, y, MODULE_ROW_HEIGHT, TEXT, false, Align.LEFT);

		if (ConfigManager.get().descriptions) {
			String hint = module.getCategory().getDisplayName();
			RenderUtils.drawTextVCentered(context, hint, x, y + 11.0f, 12.0f, TEXT_FAINT, false, Align.LEFT);
		}

		ToggleWidget toggle = this.toggles.computeIfAbsent(module.getName(),
				name -> new ToggleWidget(module.isEnabled()));
		toggle.setOn(module.isEnabled());
		toggle.setOnToggle(() -> {
			module.toggle();
			ConfigManager.get().moduleStates.put(module.getName(), module.isEnabled());
			ConfigManager.save();
		});
		toggle.draw(context, x + width - ToggleWidget.widgetWidth(),
				y + (MODULE_ROW_HEIGHT - ToggleWidget.widgetHeight()) / 2.0f);
		this.activeWidgets.add(toggle);

		KeybindWidget keybind = this.keybinds.get(module.getName());

		if (keybind == null) {
			keybind = new KeybindWidget(module.getKeybind());
			KeybindWidget widget = keybind;
			keybind.setOnChanged(() -> {
				module.setKeybind(widget.getKeyCode());
				ConfigManager.get().moduleKeybinds.put(module.getName(), widget.getKeyCode());
				ConfigManager.save();
			});
			this.keybinds.put(module.getName(), keybind);
		}

		float keybindX = x + width - ToggleWidget.widgetWidth() - 8.0f - KeybindWidget.widgetWidth();
		keybind.draw(context, keybindX, y + (MODULE_ROW_HEIGHT - KeybindWidget.widgetHeight()) / 2.0f, mouseX, mouseY);
		this.activeWidgets.add(keybind);

		return y + MODULE_ROW_HEIGHT;
	}

	// --------------------------------------------------------------- config page

	private float renderConfigPage(DrawContext context, float mouseX, float mouseY, float y) {
		float width = contentWidth();

		float fileBody = SECTION_PADDING + 16.0f + 6.0f + BUTTON_HEIGHT + SECTION_PADDING;
		Rect file = drawSection(context, "Config file", y, width, fileBody);
		float rowX = file.x() + SECTION_PADDING;
		float rowY = file.y() + SECTION_HEADER_HEIGHT + SECTION_PADDING;

		RenderUtils.drawText(context, ConfigManager.getPath().toString(), rowX, rowY, TEXT_DIM, false);
		rowY += 22.0f;

		float next = button(context, "Save", rowX, rowY, 68.0f, mouseX, mouseY, () -> {
			persistSettings();
			HudManager.savePositions();
		});
		next = button(context, "Reload", next + 8.0f, rowY, 68.0f, mouseX, mouseY, this::reloadConfig);
		button(context, "Reset", next + 8.0f, rowY, 68.0f, mouseX, mouseY, this::resetConfig);
		y = file.bottom() + SECTION_GAP;

		// ---- Profiles ----------------------------------------------------------
		List<String> profileNames = ConfigManager.listProfiles();
		float profilesBody = SECTION_PADDING + 16.0f + profileNames.size() * (BUTTON_HEIGHT + 6.0f)
				+ SECTION_PADDING;
		Rect profilesSection = drawSection(context, "Profiles", y, width, profilesBody);
		float profileX = profilesSection.x() + SECTION_PADDING;
		float profileY = profilesSection.y() + SECTION_HEADER_HEIGHT + SECTION_PADDING;

		RenderUtils.drawText(context, "Active: " + ConfigManager.getActiveProfile(), profileX, profileY,
				TEXT_DIM, false);
		profileY += 16.0f;

		for (String profile : profileNames) {
			float nextProfile = button(context, profile, profileX, profileY, 110.0f, mouseX, mouseY,
					() -> switchToProfile(profile));

			if (profile.equals(ConfigManager.getActiveProfile())) {
				RenderUtils.drawText(context, "active", nextProfile + 8.0f, profileY + 3.0f, TEXT_FAINT, false);
			}

			profileY += BUTTON_HEIGHT + 6.0f;
		}

		y = profilesSection.bottom() + SECTION_GAP;

		// ---- HUD positions ----------------------------------------------------
		List<HudModule> elements = HudManager.getElements();
		int rows = Math.max(1, elements.size()) + 1;
		float positionsBody = SECTION_PADDING + rows * 18.0f + SECTION_PADDING;
		Rect positions = drawSection(context, "HUD positions", y, width, positionsBody);

		float lineX = positions.x() + SECTION_PADDING;
		float lineY = positions.y() + SECTION_HEADER_HEIGHT + SECTION_PADDING;

		if (elements.isEmpty()) {
			RenderUtils.drawText(context, "No HUD elements registered.", lineX, lineY, TEXT_FAINT, false);
			lineY += 18.0f;
		}

		for (HudModule element : elements) {
			String state = element.isPositioned() ? "custom" : "auto";
			RenderUtils.drawText(context, element.getName(), lineX, lineY, TEXT, false);
			RenderUtils.drawText(context, state, lineX + 140.0f, lineY, TEXT_FAINT, false);
			lineY += 18.0f;
		}

		button(context, "Reset HUD positions", lineX, lineY, 140.0f, mouseX, mouseY, HudManager::resetPositions);

		return positions.bottom() + SECTION_GAP;
	}

	// ----------------------------------------------------------- hud editor page

	private float renderHudEditorPage(DrawContext context, float mouseX, float mouseY, float y) {
		float width = contentWidth();
		List<HudModule> elements = HudManager.getElements();

		int rows = Math.max(1, elements.size());
		float body = SECTION_PADDING + rows * MODULE_ROW_HEIGHT + SECTION_PADDING
				+ 8.0f + BUTTON_HEIGHT + SECTION_PADDING;
		Rect box = drawSection(context, "HUD elements", y, width, body);

		float rowX = box.x() + SECTION_PADDING;
		float rowWidth = box.width() - SECTION_PADDING * 2.0f;
		float rowY = box.y() + SECTION_HEADER_HEIGHT + SECTION_PADDING;

		if (elements.isEmpty()) {
			RenderUtils.drawTextVCentered(context, "No HUD elements registered.",
					rowX, rowY, MODULE_ROW_HEIGHT, TEXT_FAINT, false, Align.LEFT);
			rowY += MODULE_ROW_HEIGHT;
		}

		for (HudModule element : elements) {
			moduleRow(context, element, rowX, rowY, rowWidth, mouseX, mouseY);
			rowY += MODULE_ROW_HEIGHT;
		}

		rowY += 8.0f;
		float next = button(context, "Open HUD editor", rowX, rowY, 140.0f, mouseX, mouseY,
				() -> openEditor(context));
		button(context, "Reset positions", next + 8.0f, rowY, 140.0f, mouseX, mouseY, HudManager::resetPositions);

		return box.bottom() + SECTION_GAP;
	}

	private void openEditor(DrawContext context) {
		persistSettings();
		if (this.client != null) {
			this.client.setScreen(new HudEditorScreen());
		}
	}

	// ------------------------------------------------------------ row primitives

	private Rect drawSection(DrawContext context, String title, float y, float width, float bodyHeight) {
		boolean collapsed = isCollapsed(title);
		float height = SECTION_HEADER_HEIGHT + (collapsed ? 0.0f : bodyHeight);
		float x = contentX();

		RenderUtils.drawRoundedRect(context, x, y, width, height, 8.0f, SECTION_BG);
		RenderUtils.drawBorder(context, x, y, width, height, 8.0f, 1.0f, SECTION_OUTLINE);
		RenderUtils.drawTextVCentered(context, title, x + 14.0f, y, SECTION_HEADER_HEIGHT, TEXT_DIM, false, Align.LEFT);
		RenderUtils.drawTextVCentered(context, collapsed ? "\u203A" : "\u2304", x + width - 22.0f, y,
				SECTION_HEADER_HEIGHT, TEXT_DIM, false, Align.CENTER);

		this.sectionHeaders.put(title, new Rect(x, y, width, SECTION_HEADER_HEIGHT));

		return new Rect(x, y, width, height);
	}

	private boolean isCollapsed(String title) {
		return this.collapsedSections.getOrDefault(title, false);
	}

	private float toggleRow(DrawContext context, String label, String description, ToggleWidget toggle,
							float x, float y, float width) {
		RenderUtils.drawTextVCentered(context, label, x, y, ROW_HEIGHT, TEXT, false, Align.LEFT);

		if (ConfigManager.get().descriptions && description != null) {
			RenderUtils.drawTextVCentered(context, description,
					x + width - ToggleWidget.widgetWidth() - 10.0f, y, ROW_HEIGHT, TEXT_FAINT, false, Align.RIGHT);
		}

		toggle.draw(context, x + width - ToggleWidget.widgetWidth(),
				y + (ROW_HEIGHT - ToggleWidget.widgetHeight()) / 2.0f);
		this.activeWidgets.add(toggle);

		return y + ROW_HEIGHT;
	}

	private float sliderRow(DrawContext context, String label, SliderWidget slider, String valueText,
							float x, float y, float width) {
		slider.draw(context, x, y, width, label, valueText);
		this.activeWidgets.add(slider);

		return y + ROW_HEIGHT;
	}

	private float dropdownRow(DrawContext context, DropdownWidget dropdown, float x, float y, float width,
							  float mouseX, float mouseY) {
		dropdown.draw(context, x, y, width, mouseX, mouseY);
		this.activeWidgets.add(dropdown);

		return y + dropdown.getHeight();
	}

	private float button(DrawContext context, String label, float x, float y, float width,
						 float mouseX, float mouseY, Runnable action) {
		boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BUTTON_HEIGHT;

		RenderUtils.drawRoundedRect(context, x, y, width, BUTTON_HEIGHT, 4.0f,
				hovered ? BUTTON_BG_HOVER : BUTTON_BG);
		RenderUtils.drawBorder(context, x, y, width, BUTTON_HEIGHT, 4.0f, 1.0f, WINDOW_BORDER);
		RenderUtils.drawTextVCentered(context, label, x + width / 2.0f, y, BUTTON_HEIGHT, TEXT, false, Align.CENTER);

		this.activeButtons.add(new Button(new Rect(x, y, width, BUTTON_HEIGHT), action));

		return x + width;
	}

	private void updateHover(double mouseX, double mouseY) {
		this.hoveredRow = null;

		for (Row row : this.rows) {
			if (row.rect().contains(mouseX, mouseY)) {
				this.hoveredRow = row;
				return;
			}
		}
	}

	// -------------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		layout();

		double mouseX = toLocalX(click.x());
		double mouseY = toLocalY(click.y());

		// Widgets cache their hit boxes in local window space, so they must be handed the converted
		// click —passing the raw screen-space one silently misses whenever the window is scaled.
		Click localClick = new Click(mouseX, mouseY, click.buttonInfo());

		// The settings panel overlays the module rows, so it consumes clicks before anything underneath.
		if (this.settingsPanel.mouseClicked(localClick)) {
			return true;
		}

		if (this.closeButton.contains(mouseX, mouseY)) {
			close();
			return true;
		}

		if (this.searchButton.contains(mouseX, mouseY)) {
			this.searching = !this.searching;
			this.searchQuery = "";
			this.scroll = 0.0f;
			return true;
		}

		for (Row row : this.rows) {
			if (row.rect().contains(mouseX, mouseY)) {
				this.selected = row.entry().target();
				this.searching = false;
				this.scroll = 0.0f;
				return true;
			}
		}

		for (Map.Entry<String, Rect> header : this.sectionHeaders.entrySet()) {
			if (header.getValue().contains(mouseX, mouseY)) {
				this.collapsedSections.put(header.getKey(), !isCollapsed(header.getKey()));
				return true;
			}
		}

		for (Button entry : this.activeButtons) {
			if (entry.rect().contains(mouseX, mouseY)) {
				entry.action().run();
				return true;
			}
		}

		// Widgets cache their hit boxes in local window space, so they must be handed the converted
		// click —passing the raw screen-space one silently misses whenever the window is scaled.
		for (GuiWidget widget : this.activeWidgets) {
			if (widget.mouseClicked(localClick)) {
				return true;
			}
		}

		// Reached only when the click missed every widget on the row, i.e. it landed on the module name.
		for (Map.Entry<String, Rect> entry : this.moduleRows.entrySet()) {
			if (!entry.getValue().contains(mouseX, mouseY)) {
				continue;
			}

			Module module = ModuleManager.get(entry.getKey());

			if (module != null && module.hasSettings()) {
				this.settingsPanel.open(module);
			}

			return true;
		}

		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		layout();

		Click localClick = new Click(toLocalX(click.x()), toLocalY(click.y()), click.buttonInfo());

		if (this.settingsPanel.mouseDragged(localClick, offsetX / this.scale, offsetY / this.scale)) {
			return true;
		}

		for (GuiWidget widget : this.activeWidgets) {
			if (widget.mouseDragged(localClick, offsetX / this.scale, offsetY / this.scale)) {
				return true;
			}
		}

		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(Click click) {
		layout();

		Click localClick = new Click(toLocalX(click.x()), toLocalY(click.y()), click.buttonInfo());
		boolean handled = this.settingsPanel.mouseReleased(localClick);

		for (GuiWidget widget : this.activeWidgets) {
			if (widget.mouseReleased(localClick)) {
				handled = true;
			}
		}

		if (handled) {
			persistSettings();
			return true;
		}

		return super.mouseReleased(click);
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		updateHover(toLocalX(mouseX), toLocalY(mouseY));
		super.mouseMoved(mouseX, mouseY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		layout();

		if (this.settingsPanel.mouseScrolled(toLocalX(mouseX), toLocalY(mouseY), verticalAmount)) {
			return true;
		}

		if (toLocalX(mouseX) < SIDEBAR_WIDTH) {
			return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		}

		this.scroll -= (float) verticalAmount * SCROLL_STEP;
		this.scroll = Math.max(0.0f, Math.min(maxScroll(), this.scroll));
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		// A key bind capture always wins, including Escape (which cancels the capture).
		for (GuiWidget widget : this.activeWidgets) {
			if (widget.keyPressed(input)) {
				return true;
			}
		}

		if (this.searching) {
			if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
				this.searching = false;
				this.searchQuery = "";
				return true;
			}

			if (input.key() == GLFW.GLFW_KEY_BACKSPACE && !this.searchQuery.isEmpty()) {
				this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
				return true;
			}
		}

		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharInput input) {
		if (this.searching && input.isValidChar()) {
			this.searchQuery += input.asString();
			return true;
		}

		return super.charTyped(input);
	}
}
