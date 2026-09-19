package com.sakura.client.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes vanilla's own critical-hit test.
 *
 * <p>{@code isCriticalHit} is the exact predicate {@code PlayerEntity.attack} uses to decide whether an attack
 * crits. Calling it instead of re-writing the conditions means the Criticals module cannot drift from vanilla
 * when the conditions change 鈥?and the conditions are the whole point of the module.</p>
 */
@Mixin(PlayerEntity.class)
public interface PlayerEntityAccessor {

	/** @return true when an attack on this target, right now, would be a critical hit */
	@Invoker("isCriticalHit")
	boolean sakura$isCriticalHit(Entity target);
}
