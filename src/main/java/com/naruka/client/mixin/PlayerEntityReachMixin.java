package com.naruka.client.mixin;

import com.naruka.client.module.impl.ReachModule;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends the interaction ranges the Reach module reports.
 *
 * <p>Both methods are injected at their return so the module adds to the value vanilla computed from the player's
 * attributes instead of replacing it.</p>
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityReachMixin {

	@Inject(method = "getEntityInteractionRange()D", at = @At("RETURN"), cancellable = true)
	private void naruka$extendEntityRange(CallbackInfoReturnable<Double> info) {
		double vanillaValue = info.getReturnValueD();
		double extended = ReachModule.extendEntityRange(vanillaValue);

		if (extended != vanillaValue) {
			info.setReturnValue(extended);
		}
	}

	@Inject(method = "getBlockInteractionRange()D", at = @At("RETURN"), cancellable = true)
	private void naruka$extendBlockRange(CallbackInfoReturnable<Double> info) {
		double vanillaValue = info.getReturnValueD();
		double extended = ReachModule.extendBlockRange(vanillaValue);

		if (extended != vanillaValue) {
			info.setReturnValue(extended);
		}
	}
}
