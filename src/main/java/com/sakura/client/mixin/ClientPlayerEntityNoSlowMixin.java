package com.sakura.client.mixin;

import com.sakura.client.module.impl.NoSlowModule;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the movement input through unmodified while an item-use slowdown is being ignored.
 *
 * <p>{@code applyMovementSpeedFactors} is private and takes the movement vector and returns the scaled one, so
 * the whole penalty is cancelled by returning the argument unchanged. Verified against the 1.21.11 bytecode: the
 * method exists with the signature {@code (Lnet/minecraft/util/math/Vec2f;)Lnet/minecraft/util/math/Vec2f;} and
 * is called from {@code tickMovementInput}, which is where the result is written to the player's forward and
 * sideways speed.</p>
 *
 * <p>The cancel is narrow on purpose. It happens only while {@link NoSlowModule} says the action in progress is
 * one the player switched on, and it returns the input untouched rather than computing a partial factor, so
 * every other penalty the same method applies — sneaking above all — still applies whenever the module is off or
 * the action is not one of its categories.</p>
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityNoSlowMixin {

	@Inject(method = "applyMovementSpeedFactors(Lnet/minecraft/util/math/Vec2f;)Lnet/minecraft/util/math/Vec2f;",
			at = @At("HEAD"), cancellable = true)
	private void sakura$skipItemUseSlowdown(Vec2f movementInput, CallbackInfoReturnable<Vec2f> info) {
		if (NoSlowModule.shouldSkipSlowdown()) {
			info.setReturnValue(movementInput);
		}
	}
}
