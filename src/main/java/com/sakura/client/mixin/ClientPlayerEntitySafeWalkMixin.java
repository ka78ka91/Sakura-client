package com.sakura.client.mixin;

import com.sakura.client.module.impl.SafeWalkModule;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Scales the movement input down while SafeWalk is holding the player at an edge.
 *
 * <p>The hook sits on the parameter of {@code applyMovementSpeedFactors}, which is the one place the client's
 * movement input is turned into the forward and sideways speed the game moves the player by. Modifying the
 * argument rather than cancelling the method matters: the same method also applies the item-use and sneaking
 * penalties, and cancelling it would disable all of them — that is NoSlow's job, not this module's.</p>
 *
 * <p>{@code @ModifyVariable} on the first argument is used because the parameter has no name in the mappings and
 * because it works whether the value arrives from the field or from anywhere else, so a version that rearranges
 * the surrounding bytecode does not silently detach the hook.</p>
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntitySafeWalkMixin {

	@ModifyVariable(method = "applyMovementSpeedFactors(Lnet/minecraft/util/math/Vec2f;)Lnet/minecraft/util/math/Vec2f;",
			at = @At("HEAD"), argsOnly = true)
	private Vec2f sakura$slowAtEdge(Vec2f movementInput) {
		return SafeWalkModule.maybeSlowAtEdge(movementInput);
	}
}
