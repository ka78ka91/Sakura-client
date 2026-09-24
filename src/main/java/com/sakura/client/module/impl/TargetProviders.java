package com.sakura.client.module.impl;

import net.minecraft.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * The list of modules that can hand the TargetHUD a target.
 *
 * <p>A registry rather than a direct reference from the HUD to the combat modules: the HUD lives in
 * {@code hud.element} and has no business knowing that KillAura and Aimbot exist, and the aura should not have
 * to know that a HUD element is reading it. Modules add themselves in their constructor, which is safe because
 * {@code ModuleManager} is what constructs them and it is populated once at client start-up.</p>
 *
 * <p>Order is priority. The first provider that reports itself as providing a target wins, so an enabled aura
 * beats an enabled aimbot, and both beat the plain crosshair the HUD falls back to when nothing claims one.
 * {@link #current()} returns {@code null} in that case, which is the HUD's signal to look at the crosshair.</p>
 */
public final class TargetProviders {

	private static final List<TargetProvider> PROVIDERS = new ArrayList<>();

	private TargetProviders() {
	}

	/** Registers a module as a target source. Called from the module's own constructor. */
	public static void register(TargetProvider provider) {
		if (provider != null && !PROVIDERS.contains(provider)) {
			PROVIDERS.add(provider);
		}
	}

	/**
	 * @return the target the highest-priority active module is working on, or {@code null} when no module has one
	 */
	public static LivingEntity current() {
		for (TargetProvider provider : PROVIDERS) {
			if (!provider.isProviding()) {
				continue;
			}

			LivingEntity target = provider.getTarget();

			if (target != null) {
				return target;
			}
		}

		return null;
	}

	/** Clears the registry. Used when the module list is rebuilt, e.g. by a configuration reset. */
	public static void clear() {
		PROVIDERS.clear();
	}
}
