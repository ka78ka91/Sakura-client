package com.sakura.client.module.impl;

import net.minecraft.entity.LivingEntity;

/**
 * Something on the client that is currently tracking an entity, so the TargetHUD can show it.
 *
 * <p>The TargetHUD started out reading the crosshair, which is all it could do while the client had no module
 * that picked its own target: its own documentation said LiquidBounce listens to its KillAura's target and that
 * this client had no KillAura to listen to. Now that the combat modules do pick targets, the crosshair is the
 * least interesting of the available answers — an aura's target is the one the player cares about, and it is the
 * one that has health worth watching.</p>
 *
 * <p>Implementations are registered once, at construction, and are asked <em>every tick</em>. A module that has
 * nothing in view must return {@code null} rather than a stale target; the HUD's own hold timer is what keeps
 * the panel on screen for a moment after a target is lost.</p>
 */
public interface TargetProvider {

	/**
	 * @return the entity this module is currently working on, or {@code null} when it has none
	 */
	LivingEntity getTarget();

	/**
	 * @return whether this provider should win over the ones that come after it
	 *
	 * <p>Providers that are switched off answer {@code false}, so simply leaving a module disabled is enough to
	 * hand the HUD back to the crosshair.</p>
	 */
	boolean isProviding();
}
