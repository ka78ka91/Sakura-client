package com.sakura.client.mixin;

import com.sakura.client.module.impl.VelocityModule;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the Velocity module every knockback the server applies to the local player.
 *
 * <p>Injected at the head of the handler so a mode can stop the packet before it reaches the player, and so every
 * mode sees the same event whether or not it cancels it.</p>
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class VelocityPacketMixin {

	@Inject(method = "onEntityVelocityUpdate(Lnet/minecraft/network/packet/s2c/play/EntityVelocityUpdateS2CPacket;)V",
			at = @At("HEAD"))
	private void sakura$onVelocity(EntityVelocityUpdateS2CPacket packet, CallbackInfo info) {
		VelocityModule.onVelocityPacket(packet);
	}
}
