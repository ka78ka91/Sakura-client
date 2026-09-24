package com.sakura.client.render;

import com.sakura.client.config.ConfigManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The single source of Sakura's colours: five named palettes, one of them current, every surface in the
 * ClickGUI, the HUD editor, the widgets, the notifications and the HUD glass reading its values from here.
 *
 * <p>Two colour sources stay deliberately separate. The palettes own <em>surfaces</em> —window, sidebar,
 * sections, text tiers, glass —so switching a theme recolours the whole client consistently. The
 * <em>accent</em> stays in the config ({@code accentColor}); a theme switch merely re-seeds it with the
 * palette's own accent, after which the player can still pick a personal one without the theme fighting
 * back. Data-visualisation colours (FPS and ping temperature ramps, health and durability bars, effect
 * tints) are not theme material and remain where they are.</p>
 */
public final class Theme {

	/** The five built-in palettes. */
	public enum Palette {
		SAKURA("Sakura"),
		MIDNIGHT("Midnight"),
		MONO("Mono"),
		SUNSET("Sunset"),
		FOREST("Forest");

		private final String label;

		Palette(String label) {
			this.label = label;
		}

		public String label() {
			return this.label;
		}
	}

	// -------------------------------------------------------------------- state
	private static Palette current = Palette.SAKURA;
	private static final Map<Palette, Theme> PALETTES = buildPalettes();

	private final int accent;
	private final int windowBg;
	private final int windowBorder;
	private final int sidebarBg;
	private final int sidebarDivider;
	private final int rowSelected;
	private final int rowHover;
	private final int rowText;
	private final int rowIcon;
	private final int sectionBg;
	private final int sectionOutline;
	private final int headerDivider;
	private final int text;
	private final int textDim;
	private final int textFaint;
	private final int textMuted;
	private final int panelBg;
	private final int panelBorder;
	private final int headerBg;
	private final int chipBg;
	private final int buttonBg;
	private final int buttonBgHover;
	private final int scrollbar;
	private final int glassTop;
	private final int glassBottom;
	private final int glassBorder;
	private final int glassShadow;
	private final float radius;
	private final float hudRadius;

	private Theme(int accent, int windowBg, int windowBorder, int sidebarBg, int sidebarDivider,
				  int rowSelected, int rowHover, int rowText, int rowIcon,
				  int sectionBg, int sectionOutline, int headerDivider,
				  int text, int textDim, int textFaint, int textMuted,
				  int panelBg, int panelBorder, int headerBg, int chipBg,
				  int buttonBg, int buttonBgHover, int scrollbar,
				  int glassTop, int glassBottom, int glassBorder, int glassShadow,
				  float radius, float hudRadius) {
		this.accent = accent;
		this.windowBg = windowBg;
		this.windowBorder = windowBorder;
		this.sidebarBg = sidebarBg;
		this.sidebarDivider = sidebarDivider;
		this.rowSelected = rowSelected;
		this.rowHover = rowHover;
		this.rowText = rowText;
		this.rowIcon = rowIcon;
		this.sectionBg = sectionBg;
		this.sectionOutline = sectionOutline;
		this.headerDivider = headerDivider;
		this.text = text;
		this.textDim = textDim;
		this.textFaint = textFaint;
		this.textMuted = textMuted;
		this.panelBg = panelBg;
		this.panelBorder = panelBorder;
		this.headerBg = headerBg;
		this.chipBg = chipBg;
		this.buttonBg = buttonBg;
		this.buttonBgHover = buttonBgHover;
		this.scrollbar = scrollbar;
		this.glassTop = glassTop;
		this.glassBottom = glassBottom;
		this.glassBorder = glassBorder;
		this.glassShadow = glassShadow;
		this.radius = radius;
		this.hudRadius = hudRadius;
	}

	// --------------------------------------------------------------------- api

	public static Palette palette() {
		return current;
	}

	/** @return every palette label, in menu order, for the Settings dropdown */
	public static String[] labels() {
		Palette[] values = Palette.values();
		String[] labels = new String[values.length];

		for (int index = 0; index < values.length; index++) {
			labels[index] = values[index].label();
		}

		return labels;
	}

	/** @return the dropdown index of a palette label, or 0 when the name is unknown */
	public static int indexOf(String label) {
		for (int index = 0; index < labels().length; index++) {
			if (labels()[index].equalsIgnoreCase(label)) {
				return index;
			}
		}

		return 0;
	}

	/** Switches the active palette by label; unknown names keep the current one. */
	public static void set(String label) {
		for (Palette candidate : Palette.values()) {
			if (candidate.label().equalsIgnoreCase(label)) {
				current = candidate;
				return;
			}
		}
	}

	/**
	 * @return the user accent at the given alpha, so accent-tinted surfaces stay on one hue when the
	 * player picks a personal colour
	 */
	public static int accentAlpha(int alpha) {
		return (ConfigManager.get().accentColor & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
	}

	/** @return the current palette's own accent, used to re-seed the config accent on a theme switch */
	public static int accent() {
		return current().accent;
	}

	public static int windowBg() {
		return current().windowBg;
	}

	public static int windowBorder() {
		return current().windowBorder;
	}

	public static int sidebarBg() {
		return current().sidebarBg;
	}

	public static int sidebarDivider() {
		return current().sidebarDivider;
	}

	public static int rowSelected() {
		return current().rowSelected;
	}

	public static int rowHover() {
		return current().rowHover;
	}

	public static int rowText() {
		return current().rowText;
	}

	public static int rowIcon() {
		return current().rowIcon;
	}

	public static int sectionBg() {
		return current().sectionBg;
	}

	public static int sectionOutline() {
		return current().sectionOutline;
	}

	public static int headerDivider() {
		return current().headerDivider;
	}

	public static int text() {
		return current().text;
	}

	public static int textDim() {
		return current().textDim;
	}

	public static int textFaint() {
		return current().textFaint;
	}

	public static int textMuted() {
		return current().textMuted;
	}

	public static int panelBg() {
		return current().panelBg;
	}

	public static int panelBorder() {
		return current().panelBorder;
	}

	public static int headerBg() {
		return current().headerBg;
	}

	public static int chipBg() {
		return current().chipBg;
	}

	public static int buttonBg() {
		return current().buttonBg;
	}

	public static int buttonBgHover() {
		return current().buttonBgHover;
	}

	public static int scrollbar() {
		return current().scrollbar;
	}

	public static int glassTop() {
		return current().glassTop;
	}

	public static int glassBottom() {
		return current().glassBottom;
	}

	public static int glassBorder() {
		return current().glassBorder;
	}

	public static int glassShadow() {
		return current().glassShadow;
	}

	/** Corner radius of the ClickGUI window frame. */
	public static float radius() {
		return current().radius;
	}

	/** Base HUD glass radius for elements that do not derive their own. */
	public static float hudRadius() {
		return current().hudRadius;
	}

	private static Theme current() {
		return PALETTES.get(current);
	}

	// ---------------------------------------------------------------- palettes

	private static Map<Palette, Theme> buildPalettes() {
		Map<Palette, Theme> palettes = new LinkedHashMap<>();

		// Sakura: the client's original purple-on-charcoal look, kept exactly as it shipped.
		palettes.put(Palette.SAKURA, new Theme(
				0xFFA06EFF,
				0xE0141414, 0x1AFFFFFF, 0x800F0F0F, 0x14FFFFFF,
				0x20FFFFFF, 0x15FFFFFF, 0xFFCCCCCC, 0xB3FFFFFF,
				0x33000000, 0x08FFFFFF, 0x0DFFFFFF,
				0xFFFFFFFF, 0xFFAAAAAA, 0x80FFFFFF, 0xFFCCCCCC,
				0xF01A1A1A, 0x24FFFFFF, 0x2AFFFFFF, 0x1AFFFFFF,
				0x1AFFFFFF, 0x26FFFFFF, 0x33FFFFFF,
				0xB414141A, 0x8C0A0A0F, 0x2EFFFFFF, 0x66000000,
				8.0f, 16.0f));

		// Midnight: deep navy surfaces with a cool blue accent.
		palettes.put(Palette.MIDNIGHT, new Theme(
				0xFF7AA2FF,
				0xE00E1220, 0x1AF0F4FF, 0x800A0E18, 0x14F0F4FF,
				0x20F0F4FF, 0x15F0F4FF, 0xFFC8D4F0, 0xB3E8EEFF,
				0x33000010, 0x08F0F4FF, 0x0DF0F4FF,
				0xFFFFFFFF, 0xFF9FAECB, 0x80F0F4FF, 0xFFC8D4F0,
				0xF0121626, 0x24F0F4FF, 0x2AF0F4FF, 0x1AF0F4FF,
				0x1AF0F4FF, 0x26F0F4FF, 0x33F0F4FF,
				0xB40E141E, 0x8C080C14, 0x2EF0F4FF, 0x66000008,
				8.0f, 16.0f));

		// Mono: pure greyscale, white accent — for players who want the UI to disappear.
		palettes.put(Palette.MONO, new Theme(
				0xFFF2F2F2,
				0xE0141414, 0x1AFFFFFF, 0x800F0F0F, 0x14FFFFFF,
				0x20FFFFFF, 0x15FFFFFF, 0xFFD6D6D6, 0xB3FFFFFF,
				0x33000000, 0x08FFFFFF, 0x0DFFFFFF,
				0xFFFFFFFF, 0xFFAAAAAA, 0x80FFFFFF, 0xFFD6D6D6,
				0xF01A1A1A, 0x24FFFFFF, 0x2AFFFFFF, 0x1AFFFFFF,
				0x1AFFFFFF, 0x26FFFFFF, 0x33FFFFFF,
				0xB4141416, 0x8C0A0A0C, 0x2EFFFFFF, 0x66000000,
				8.0f, 16.0f));

		// Sunset: warm dark browns with an ember-orange accent.
		palettes.put(Palette.SUNSET, new Theme(
				0xFFFF8A5C,
				0xE01A1210, 0x1AFFEDE4, 0x80150E0C, 0x14FFEDE4,
				0x20FFEDE4, 0x15FFEDE4, 0xFFF0DCD2, 0xB3FFEDE4,
				0x33140000, 0x08FFEDE4, 0x0DFFEDE4,
				0xFFFFFFFF, 0xFFC9AFA2, 0x80FFEDE4, 0xFFF0DCD2,
				0xF01E1614, 0x24FFEDE4, 0x2AFFEDE4, 0x1AFFEDE4,
				0x1AFFEDE4, 0x26FFEDE4, 0x33FFEDE4,
				0xB41A1310, 0x8C100A08, 0x2EFFEDE4, 0x66140000,
				8.0f, 16.0f));

		// Forest: deep green-charcoal with a leaf-green accent.
		palettes.put(Palette.FOREST, new Theme(
				0xFF7FD68A,
				0xE0101610, 0x1AEAF5EC, 0x800C120D, 0x14EAF5EC,
				0x20EAF5EC, 0x15EAF5EC, 0xFFD2E4D6, 0xB3E4F5E8,
				0x33001400, 0x08EAF5EC, 0x0DEAF5EC,
				0xFFFFFFFF, 0xFF9FB8A6, 0x80EAF5EC, 0xFFD2E4D6,
				0xF0141C16, 0x24EAF5EC, 0x2AEAF5EC, 0x1AEAF5EC,
				0x1AEAF5EC, 0x26EAF5EC, 0x33EAF5EC,
				0xB4101612, 0x8C0A100C, 0x2EEAF5EC, 0x66000E00,
				8.0f, 16.0f));

		return palettes;
	}
}
