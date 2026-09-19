package com.naruka.client.module.impl;

import com.naruka.client.module.Category;
import com.naruka.client.module.Module;
import com.naruka.client.setting.BooleanSetting;
import com.naruka.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Enlarges the targeting margin of other entities, making them easier to hit.
 *
 * <p>Ported from LiquidBounce's {@code ModuleHitbox} (GPL-3.0), including its 0.1 default. LiquidBounce also
 * applies the margin to an {@code AttackRange} item component; that class does not exist in the 1.21.11 Yarn
 * mappings, so only the entity margin path is implemented.</p>
 *
 * <p>This is the least invasive way to extend reach: nothing is sent to the server and the interaction range
 * itself is untouched, so it stays inside what a server-side range check accepts.</p>
 */
public final class HitboxModule extends Module {

	/** Set on construction so the mixin can reach the module without a registry lookup per entity per frame. */
	private static HitboxModule instance;

	private final NumberSetting size = setting(new NumberSetting("Size",
			"Extra targeting margin added to other entities.", 0.1, 0.0, 1.0, 0.05, ""));
	private final BooleanSetting playersOnly = setting(new BooleanSetting("Players Only",
			"Only enlarge the margin of other players.", true));

	public HitboxModule() {
		super("Hitbox", Category.COMBAT,
				"Enlarges the targetable box of other entities.");
		instance = this;
	}

	@Override
	public String getHudSuffix() {
		return isEnabled() ? formatSize() : null;
	}

	private String formatSize() {
		return String.format(java.util.Locale.ROOT, "%.2f", this.size.get());
	}

	/**
	 * Adds this module's margin to whatever vanilla computed.
	 *
	 * <p>Called from {@code EntityTargetingMarginMixin} for every entity whose margin is queried, so it must
	 * stay allocation free and must never throw — a broken margin computation would break the player's aim.</p>
	 *
	 * @param entity       the entity whose margin is being read
	 * @param vanillaValue the margin vanilla returned
	 * @return the margin to use
	 */
	public static float expandMargin(Entity entity, float vanillaValue) {
		HitboxModule module = instance;

		if (module == null || !module.isEnabled() || !(entity instanceof LivingEntity)) {
			return vanillaValue;
		}

		// Never widen the margin of the client's own player; that would let the player target themselves.
		if (entity == MinecraftClient.getInstance().player) {
			return vanillaValue;
		}

		if (module.playersOnly.get() && !(entity instanceof PlayerEntity)) {
			return vanillaValue;
		}

		return vanillaValue + (float) module.size.get().doubleValue();
	}
}
