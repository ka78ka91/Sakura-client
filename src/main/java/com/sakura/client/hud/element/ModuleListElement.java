package com.sakura.client.hud.element;


import com.sakura.client.render.Theme;
import com.sakura.client.config.ConfigManager;
import com.sakura.client.hud.HudAnchor;
import com.sakura.client.hud.HudModule;
import com.sakura.client.module.Module;
import com.sakura.client.module.ModuleManager;
import com.sakura.client.render.Animations;
import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.RenderUtils.Align;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Right-aligned list of every enabled module, drawn as {@code Name Suffix} rows.
 *
 * <p>This replaces the prototype's hard-coded mock entries with the live {@link ModuleManager}
 * registry, so the list always reflects what is actually switched on.</p>
 *
 * <p>Rows are kept as state rather than being rebuilt from the registry every frame: a module that is switched
 * on fades and slides into its slot, and one that is switched off fades out before its row is dropped. A row
 * that is on its way out keeps the position it had, so the surviving rows do not jump the moment a module is
 * disabled.</p>
 *
 * <p>{@link #getWidth()} and {@link #getHeight()} are measured from the rows that are actually being drawn,
 * including the ones still fading, so the box the HUD editor hit-tests is the box that is on screen.</p>
 */
public class ModuleListElement extends HudModule {

	private static final float ROW_HEIGHT = 13.0f;
	private static final float ROW_GAP = 1.0f;
	private static final float PADDING_X = 5.0f;
	private static final float MARGIN = 4.0f;
	/**
	 * Horizontal breathing room between a row's name and its mode suffix.
	 *
	 * <p>The suffix is drawn in the dim text tier rather than as part of the same string, so the two runs need
	 * explicit separation: the vanilla font's space advance is a single pixel, which reads as the name running
	 * straight into its own suffix. Rows that are too narrow for the name at this gap lose the tail of the name
	 * to an ellipsis instead of eating into the gap.</p>
	 */
	private static final float NAME_GAP = 6.0f;
	/** Accent rail on every row, parked just right of the text. */
	private static final float STRIPE_WIDTH = 2.0f;
	private static final float STRIPE_INSET = 2.0f;
	private static final float STRIPE_MARGIN = 3.0f;
	/** How far a row travels while it appears or disappears. */
	private static final float SLIDE_DISTANCE = 5.0f;
	private static final float ROW_IN_SPEED = 11.0f;
	private static final float ROW_OUT_SPEED = 7.0f;
	private static final float SLIDE_SPEED = 13.0f;
	private static final float VISIBLE_EPSILON = 0.01f;
	/** Glass body: a dark, slightly cool gradient drawn over the blurred world. */
	private static final float GLASS_SHADOW_SPREAD = 2.0f;

	private final Animations.Clock clock = new Animations.Clock();
	/** Persistent animation state, one entry per row that is on screen or still leaving it. */
	private final List<Row> rows = new ArrayList<>();
	/** Scratch copy of the enabled modules, refilled in place so the render path allocates nothing. */
	private final List<Module> enabled = new ArrayList<>();
	/** Measured once per frame in render and read for free by the size queries in between. */
	private boolean sizeKnown;
	private float cachedWidth;
	private float cachedHeight;

	public ModuleListElement() {
		super("Module List", "Right-aligned list of every enabled module",
				HudAnchor.TOP_RIGHT, MARGIN, MARGIN, false);
	}

	@Override
	public float getWidth() {
		ensureMeasured();
		return this.cachedWidth;
	}

	@Override
	public float getHeight() {
		ensureMeasured();
		return this.cachedHeight;
	}

	/** Measures from the current rows, syncing first only when nothing has been measured yet. */
	private void ensureMeasured() {
		if (this.sizeKnown) {
			return;
		}

		syncRows();
		measure();
	}

	/** Caches the drawn extent so the per-frame size queries stop re-scanning the rows. */
	private void measure() {
		float widest = 0.0f;
		int drawn = 0;

		for (Row row : this.rows) {
			if (!isDrawn(row)) {
				continue;
			}

			widest = Math.max(widest, rowWidth(row.module));
			drawn++;
		}

		this.cachedWidth = widest <= 0.0f ? 0.0f : widest + PADDING_X * 2.0f;
		this.cachedHeight = drawn == 0 ? 0.0f : drawn * ROW_HEIGHT + (drawn - 1) * ROW_GAP;
		this.sizeKnown = true;
	}

	@Override
	public void render(DrawContext context, float x, float y) {
		// The one syncRows() of the frame happens here; the size queries below read the measurement.
		syncRows();

		float delta = this.clock.tick();

		advance(delta);
		measure();

		float width = getWidth();

		if (width <= 0.0f) {
			return;
		}

		float radius = Math.min(cornerRadius(), ROW_HEIGHT / 2.0f);
		float rowY = y;

		for (Row row : this.rows) {
			if (!isDrawn(row)) {
				continue;
			}

			float offset = row.slide;
			// A row that is mid-slide is clipped to its own slot, so it can never reach outside the element.
			boolean clipped = offset < -0.05f;

			if (clipped) {
				context.enableScissor(Math.round(x), Math.round(rowY), Math.round(x + width),
						Math.round(rowY + ROW_HEIGHT));
			}

			drawRow(context, row, x, rowY + offset, width, radius, Animations.clamp01(row.visibility));

			if (clipped) {
				context.disableScissor();
			}

			rowY += ROW_HEIGHT + ROW_GAP;
		}
	}

	/** True while a row is either wanted or still visible on its way out. */
	private static boolean isDrawn(Row row) {
		return row.active || row.visibility > VISIBLE_EPSILON;
	}

	/**
	 * Brings the row list in line with the module registry: a newly enabled module gets a row at its registry
	 * position and a disabled one is flagged inactive and keeps the position it had while it fades out.
	 */
	private void syncRows() {
		refreshEnabled();

		for (Row row : this.rows) {
			row.active = false;
		}

		for (int index = 0; index < this.enabled.size(); index++) {
			Module module = this.enabled.get(index);
			Row row = rowOf(module);

			if (row == null) {
				row = new Row(module);
				this.rows.add(row);
			}

			row.active = true;
			row.order = index;
		}

		sortRows();
	}

	/** Rebuilds the scratch list of enabled modules without allocating a new list per call. */
	private void refreshEnabled() {
		this.enabled.clear();

		for (Module module : ModuleManager.getAll()) {
			if (module.isEnabled()) {
				this.enabled.add(module);
			}
		}
	}

	private Row rowOf(Module module) {
		for (Row row : this.rows) {
			if (row.module == module) {
				return row;
			}
		}

		return null;
	}

	/** Insertion sort: the list is a couple of dozen rows at most and this allocates nothing. */
	private void sortRows() {
		for (int index = 1; index < this.rows.size(); index++) {
			Row row = this.rows.get(index);
			int position = index - 1;

			while (position >= 0 && this.rows.get(position).order > row.order) {
				this.rows.set(position + 1, this.rows.get(position));
				position--;
			}

			this.rows.set(position + 1, row);
		}
	}

	/** Eases every row's fade and slide, dropping the rows that have finished leaving. */
	private void advance(float delta) {
		for (int index = this.rows.size() - 1; index >= 0; index--) {
			Row row = this.rows.get(index);

			row.visibility = Animations.approach(row.visibility, row.active ? 1.0f : 0.0f,
					row.active ? ROW_IN_SPEED : ROW_OUT_SPEED, delta);
			row.slide = Animations.approach(row.slide, row.active ? 0.0f : -SLIDE_DISTANCE,
					SLIDE_SPEED, delta);

			if (!row.active && row.visibility <= VISIBLE_EPSILON) {
				this.rows.remove(index);
			}
		}
	}

	private void drawRow(DrawContext context, Row row, float x, float y, float width, float radius,
								float alpha) {
		RenderUtils.drawGlassPanel(context, x, y, width, ROW_HEIGHT, radius,
				RenderUtils.multiplyAlpha(Theme.glassTop(), alpha), RenderUtils.multiplyAlpha(Theme.glassBottom(), alpha),
				RenderUtils.multiplyAlpha(Theme.glassBorder(), alpha),
				RenderUtils.multiplyAlpha(Theme.glassShadow(), alpha * 0.7f), GLASS_SHADOW_SPREAD);
		RenderUtils.drawAccentWash(context, x, y, width, ROW_HEIGHT, radius,
				ConfigManager.get().accentColor, alpha * 0.8f);

		RenderUtils.drawRoundedRect(context, x + width - STRIPE_WIDTH - STRIPE_INSET, y + STRIPE_MARGIN,
				STRIPE_WIDTH, ROW_HEIGHT - STRIPE_MARGIN * 2.0f, STRIPE_WIDTH * 0.5f,
				RenderUtils.multiplyAlpha(ConfigManager.get().accentColor, alpha * 0.9f));

		String suffix = row.module.getHudSuffix();
		float textX = x + width - PADDING_X;
		float textY = y + (ROW_HEIGHT - RenderUtils.fontHeight()) / 2.0f + 1.0f;
		int nameColor = RenderUtils.multiplyAlpha(themed(Theme.text()), alpha);

		if (suffix == null) {
			RenderUtils.drawText(context, row.module.getName(), textX, textY, nameColor, true, Align.RIGHT);
			return;
		}

		// Same layout the width measurement uses, so the box the HUD editor hit-tests cannot disagree with the
		// text that is actually drawn.
		float suffixWidth = suffixWidth(suffix);
		float nameRoom = rowNameWidth(suffix);
		String name = RenderUtils.trimToWidth(row.module.getName(), nameRoom);

		RenderUtils.drawText(context, name, textX - suffixWidth, textY, nameColor, true, Align.RIGHT);
		RenderUtils.drawText(context, suffix, textX, textY,
				RenderUtils.multiplyAlpha(themed(Theme.textDim()), alpha), true, Align.RIGHT);
	}

	/** @return the total width reserved for the suffix, including the {@link #NAME_GAP} before it */
	private static float suffixWidth(String suffix) {
		return NAME_GAP + RenderUtils.textWidth(suffix);
	}

	/** @return the horizontal space a row's name may occupy before it has to be trimmed */
	private static float rowNameWidth(String suffix) {
		return suffixWidth(suffix);
	}

	/** @return the width of everything in a row except the module's own name */
	private static float rowFixedWidth(String suffix) {
		return PADDING_X + suffixWidth(suffix);
	}

	private static float rowWidth(Module module) {
		String suffix = module.getHudSuffix();

		if (suffix == null) {
			return PADDING_X * 2.0f + RenderUtils.textWidth(module.getName());
		}

		return rowFixedWidth(suffix) + RenderUtils.textWidth(module.getName());
	}

	/** One module's row, kept across frames so it can fade and slide in and out of the list. */
	private static final class Row {

		private final Module module;
		/** 0 hidden, 1 fully drawn. */
		private float visibility;
		/** Vertical offset in pixels: 0 while settled, negative while the row is leaving. */
		private float slide;
		/** Last known position in the enabled list, so a leaving row keeps its slot while it fades. */
		private int order;
		private boolean active;

		private Row(Module module) {
			this.module = module;
		}
	}
}
