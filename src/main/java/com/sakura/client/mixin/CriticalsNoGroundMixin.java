package com.sakura.client.mixin;

import com.sakura.client.module.impl.CriticalsModule;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * NoGround mode: every movement packet leaves claiming the player is airborne.
 *
 * <p>{@code sendMovementPackets} reads {@code isOnGround()} once per packet variant to fill the packet in, so
 * redirecting those reads covers every variant at once and touches nothing else —the player's own physics never
 * sees the lie.</p>
 */
@Mixin(ClientPlayerEntity.class)
public abstract class CriticalsNoGroundMixin {

	@Redirect(method = "sendMovementPackets()V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/network/ClientPlayerEntity;isOnGround()Z"))
	private boolean sakura$noGround(ClientPlayerEntity player) {
		return CriticalsModule.isNoGroundActive() ? false : player.isOnGround();
	}
}
