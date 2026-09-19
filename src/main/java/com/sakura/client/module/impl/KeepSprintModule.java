package com.sakura.client.module.impl;

import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import com.sakura.client.setting.ChanceSetting;
import com.sakura.client.setting.RangeSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Puts the player's horizontal velocity back after an attack.
 *
 * <p>Ported from LiquidBounce's {@code ModuleKeepSprint} (GPL-3.0): the same Motion / MotionWhenHurt / HurtTime
 * / Chance settings, with the percentages rolled inside their range as LiquidBounce does.</p>
 *
 * <p>The velocity before the attack is captured at the head of {@code PlayerEntity#attack} and re-applied at
 * the tail, scaled by the configured percentage. Re-applying the captured value rather than patching vanilla's
 * own arithmetic means the module behaves the same whether or not the version reduces velocity on a sprint
 * hit, and a setting below 100% deliberately slows the player instead of pretending to.</p>
 *
 * <p>When the Chance roll fails nothing is re-applied at all, so vanilla's result stands —that is the honest
 * equivalent of LiquidBounce's 0.6 fallback, which hard-codes a reduction that 1.21.11 does not perform.</p>
 */
public final class KeepSprintModule extends Module {

	private static KeepSprintModule instance;

	/** Velocity captured at the head of an attack, replayed at its tail. */
	private static Vec3d preAttackVelocity;
	private static PlayerEntity preAttackPlayer;

	private final RangeSetting motion = setting(new RangeSetting("Motion",
			"Percentage of the pre-attack velocity to keep after a hit.", 100.0, 100.0, 0.0, 100.0, false, "%"));
	private final RangeSetting motionWhenHurt = setting(new RangeSetting("Motion When Hurt",
			"Percentage to keep while your hurt time is inside the Hurt Time range.", 100.0, 100.0, 0.0, 100.0,
			false, "%"));
	private final RangeSetting hurtTime = setting(new RangeSetting("Hurt Time",
			"Hurt time range that selects Motion When Hurt over Motion.", 1.0, 10.0, 1.0, 10.0, true, ""));
	private final ChanceSetting chance = setting(new ChanceSetting("Chance",
			"Chance of applying the module to an attack.", 100.0));

	public KeepSprintModule() {
		super("KeepSprint", Category.COMBAT, "Keeps your sprint speed after hitting an entity.");
		instance = this;
	}

	@Override
	public String getHudSuffix() {
		return isEnabled() ? "KeepSprint" : null;
	}

	/** Called at the head of an attack, before vanilla can change anything. */
	public static void capturePreAttackVelocity(PlayerEntity player) {
		preAttackVelocity = null;
		preAttackPlayer = null;

		KeepSprintModule module = instance;

		if (module == null || !module.isEnabled()) {
			return;
		}

		preAttackVelocity = player.getVelocity();
		preAttackPlayer = player;
	}

	/** Called at the tail of an attack. */
	public static void restoreVelocity(PlayerEntity player) {
		Vec3d before = preAttackVelocity;
		PlayerEntity owner = preAttackPlayer;
		preAttackVelocity = null;
		preAttackPlayer = null;

		KeepSprintModule module = instance;

		if (before == null || owner != player || module == null || !module.isEnabled()) {
			return;
		}

		Double percent = module.keepPercent(player);

		if (percent == null) {
			// The chance roll failed: leave whatever vanilla did in place.
			return;
		}

		float keep = (float) (percent / 100.0);
		Vec3d after = player.getVelocity();

		// Only the horizontal part is touched: the vertical velocity is nobody's business here.
		player.setVelocity(before.x * keep, after.y, before.z * keep);
	}

	/**
	 * @param player the attacking player
	 * @return the percentage to keep, or {@code null} when the chance roll failed
	 */
	private Double keepPercent(PlayerEntity player) {
		if (!this.chance.roll()) {
			return null;
		}

		RangeSetting.RangeValue range = this.hurtTime.get();
		boolean hurt = player.hurtTime >= range.min() && player.hurtTime <= range.max();
		return hurt ? this.motionWhenHurt.random() : this.motion.random();
	}
}
