package com.naruka.client.module.impl;

import com.naruka.client.module.Category;
import com.naruka.client.module.Module;
import com.naruka.client.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;

/**
 * Removes the attack cooldown penalty for missed attacks.
 *
 * <p>Ported from LiquidBounce's {@code ModuleNoMissCooldown} (GPL-3.0), same two options and same defaults.
 * In 1.21.11 the miss path is {@code MinecraftClient#doAttack} calling {@code resetTicksSinceLastAttack()} on
 * the player when the crosshair target is a miss, which is the call this module drops.</p>
 *
 * <p>Cancel Attack On Miss is off by default because it also suppresses the swing animation, which is visible
 * to anyone watching.</p>
 */
public final class NoMissCooldownModule extends Module {

	private static NoMissCooldownModule instance;

	private final BooleanSetting removeAttackCooldown = setting(new BooleanSetting("Remove Attack Cooldown",
			"Do not reset the attack cooldown when an attack misses.", true));
	private final BooleanSetting cancelAttackOnMiss = setting(new BooleanSetting("Cancel Attack On Miss",
			"Do not swing at all when the attack would miss.", false));

	public NoMissCooldownModule() {
		super("NoMissCooldown", Category.COMBAT, "Skips the cooldown penalty for missed attacks.");
		instance = this;
	}

	/** @return true when the vanilla miss penalty should be skipped */
	public static boolean shouldRemoveMissCooldown() {
		NoMissCooldownModule module = instance;
		return module != null && module.isEnabled() && module.removeAttackCooldown.get();
	}

	/**
	 * @param client the client whose crosshair is being checked
	 * @return true when the attack should be dropped before it swings
	 */
	public static boolean shouldCancelMissAttack(MinecraftClient client) {
		NoMissCooldownModule module = instance;

		if (module == null || !module.isEnabled() || !module.cancelAttackOnMiss.get()) {
			return false;
		}

		HitResult target = client.crosshairTarget;
		return target == null || target.getType() == HitResult.Type.MISS;
	}
}
