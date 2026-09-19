package com.sakura.client.mixin;

import com.sakura.client.rotation.RotationManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts a silent rotation into the movement packet and takes it back out again.
 *
 * <p>The swap lives for exactly the duration of {@code sendMovementPackets}, so the packet carries the module's
 * angle while the camera, the player's input and the movement that was already computed for this tick all keep
 * using the real one. That is why this port needs no movement correction, unlike LiquidBounce's packet rewriting.</p>
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityRotationMixin {

	@Inject(method = "sendMovementPackets()V", at = @At("HEAD"))
	private void sakura$swapInSilentRotation(CallbackInfo info) {
		RotationManager.beginPacket((ClientPlayerEntity) (Object) this);
	}

	@Inject(method = "sendMovementPackets()V", at = @At("RETURN"))
	private void sakura$swapOutSilentRotation(CallbackInfo info) {
		RotationManager.endPacket((ClientPlayerEntity) (Object) this);
	}
}
