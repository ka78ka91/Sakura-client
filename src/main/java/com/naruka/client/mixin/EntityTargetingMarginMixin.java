package com.naruka.client.mixin;

import com.naruka.client.module.impl.HitboxModule;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Widens the box the crosshair can target, which is how the Hitbox module works.
 *
 * <p>Uses the 1.21.11 targeting model rather than patching attack distance: the client decides what it is
 * aiming at through {@link Entity#getTargetingMargin()}, so adding a margin changes which entities are
 * targetable without touching a single packet or the interaction range. Verified present in the 1.21.11 Yarn
 * mappings before writing this.</p>
 *
 * <p>{@code at = RETURN} is used so the original margin is available: the module adds to whatever vanilla
 * returned instead of replacing it.</p>
 */
@Mixin(Entity.class)
public abstract class EntityTargetingMarginMixin {

	@Inject(method = "getTargetingMargin()F", at = @At("RETURN"), cancellable = true)
	private void naruka$expandTargetingMargin(CallbackInfoReturnable<Float> info) {
		float expanded = HitboxModule.expandMargin((Entity) (Object) this, info.getReturnValueF());

		if (expanded != info.getReturnValueF()) {
			info.setReturnValue(expanded);
		}
	}
}
