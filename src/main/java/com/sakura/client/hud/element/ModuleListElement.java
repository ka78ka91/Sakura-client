package com.sakura.client.hud.element;

import com.sakura.client.hud.HudAnchor;
import com.sakura.client.hud.HudModule;
import com.sakura.client.module.Module;
import com.sakura.client.module.ModuleManager;
import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.RenderUtils.Align;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * Right-aligned list of every enabled module, drawn as {@code Name Suffix} rows.
 *
 * <p>This replaces the prototype's hard-coded mock entries with the live {@link ModuleManager}
 * registry, so the list always reflects what is actually switched on.</p>
 */
public class ModuleListElement extends HudModule {

	private static final float ROW_HEIGHT = 13.0f;
	private static final float ROW_GAP = 1.0f;
	private static final float PADDING_X = 5.0f;
	private static final float MARGIN = 4.0f;

	private static final int ROW_BG = 0x66000000;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFAAAAAA;

	public ModuleListElement() {
		super("Module List", "Right-aligned list of every enabled module",
				HudAnchor.TOP_RIGHT, MARGIN, MARGIN, true);
	}

	@Override
	public float getWidth() {
		List<Module> modules = ModuleManager.getEnabled();

		if (modules.isEmpty()) {
			return 0.0f;
		}

		float widest = 0.0f;

		for (Module module : modules) {
			widest = Math.max(widest, rowWidth(module));
		}

		return widest + PADDING_X * 2.0f;
	}

	@Override
	public float getHeight() {
		int count = ModuleManager.getEnabled().size();
		return count == 0 ? 0.0f : count * ROW_HEIGHT + (count - 1) * ROW_GAP;
	}

	@Override
	public void render(DrawContext context, float x, float y) {
		List<Module> modules = ModuleManager.getEnabled();

		if (modules.isEmpty()) {
			return;
		}

		float width = getWidth();
		float radius = Math.min(cornerRadius(), ROW_HEIGHT / 2.0f);
		float rowY = y;

		for (Module module : modules) {
			RenderUtils.drawRoundedRect(context, x, rowY, width, ROW_HEIGHT, radius, ROW_BG);

			String suffix = module.getHudSuffix();
			float textX = x + width - PADDING_X;
			float textY = rowY + (ROW_HEIGHT - RenderUtils.fontHeight()) / 2.0f + 1.0f;

			if (suffix == null) {
				RenderUtils.drawText(context, module.getName(), textX, textY, TEXT, true, Align.RIGHT);
			} else {
				float suffixWidth = RenderUtils.textWidth(suffix) + RenderUtils.textWidth(" ");
				RenderUtils.drawText(context, module.getName(), textX - suffixWidth, textY, TEXT, true, Align.RIGHT);
				RenderUtils.drawText(context, suffix, textX, textY, TEXT_DIM, true, Align.RIGHT);
			}

			rowY += ROW_HEIGHT + ROW_GAP;
		}
	}

	private static float rowWidth(Module module) {
		float width = RenderUtils.textWidth(module.getName());
		String suffix = module.getHudSuffix();

		if (suffix != null) {
			width += RenderUtils.textWidth(" ") + RenderUtils.textWidth(suffix);
		}

		return width;
	}
}
