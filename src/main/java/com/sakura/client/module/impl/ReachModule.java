package com.sakura.client.module.impl;

import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import com.sakura.client.setting.NumberSetting;
import com.sakura.client.setting.Risk;

/**
 * Extends the interaction ranges the client uses to decide what is in reach.
 *
 * <p>Modelled on LiquidBounce's {@code ModuleReach} (GPL-3.0). Both ranges are expressed as an <em>extra</em>
 * amount added to whatever vanilla computed, so the vanilla attribute value is never replaced and the module
 * cannot shorten a range by accident.</p>
 *
 * <p>Detectable: the server validates interaction distance with its own value and has no way to know the client
 * disagrees, so a large setting will show up as attacks and block placements that are out of range.</p>
 *
 * <p>The vanilla ranges are deliberately not hard-coded anywhere: they come from the player's attributes and
 * measured 5.0 for both the entity and block range in this build, not the 3.0 and 4.5 that older versions used.</p>
 */
public final class ReachModule extends Module {

	private static ReachModule instance;

	private final NumberSetting entityReach = setting(new NumberSetting("Entity Reach",
			"Extra blocks added to the entity interaction range vanilla computed.", 0.1, 0.0, 3.0, 0.1, "blocks"));
	private final NumberSetting blockReach = setting(new NumberSetting("Block Reach",
			"Extra blocks added to the block interaction range vanilla computed.", 0.0, 0.0, 3.0, 0.1, "blocks"));

	public ReachModule() {
		super("Reach", Category.COMBAT, "Extends how far you can attack and interact.");
		instance = this;
	}

	@Override
	public String getHudSuffix() {
		if (!isEnabled()) {
			return null;
		}

		return "+" + String.format(java.util.Locale.ROOT, "%.1f", this.entityReach.get());
	}

	/**
	 * The module extends the range the client claims, while the server validates every interaction against
	 * its own value, so any non-zero extension is a claim the server can catch contradicting. The risk comes
	 * from the mechanism rather than the magnitude, which is why even the quiet default labels as risky.
	 */
	@Override
	public Risk getRisk() {
		return Risk.RISKY;
	}

	/**
	 * @param vanillaValue the range vanilla computed, from the entity interaction range attribute
	 * @return the entity interaction range to use
	 */
	public static double extendEntityRange(double vanillaValue) {
		ReachModule module = instance;

		if (module == null || !module.isEnabled()) {
			return vanillaValue;
		}

		return vanillaValue + module.entityReach.get();
	}

	/**
	 * @param vanillaValue the range vanilla computed
	 * @return the block interaction range to use
	 */
	public static double extendBlockRange(double vanillaValue) {
		ReachModule module = instance;

		if (module == null || !module.isEnabled()) {
			return vanillaValue;
		}

		return vanillaValue + module.blockReach.get();
	}
}
