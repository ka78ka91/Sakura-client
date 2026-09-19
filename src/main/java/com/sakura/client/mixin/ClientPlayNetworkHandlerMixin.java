package com.sakura.client.mixin;

import com.sakura.client.safety.FlagDetector;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds the {@link FlagDetector} with the server's position corrections.
 *
 * <p>{@code onPlayerPositionLook} is the 1.21.11 handler for the packet that teleports the client back to where
 * the server believes it is 鈥?the clearest signal that our movement claims were rejected.</p>
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

	@Inject(method = "onPlayerPositionLook(Lnet/minecraft/network/packet/s2c/play/PlayerPositionLookS2CPacket;)V",
			at = @At("HEAD"))
	private void sakura$flagOnPositionCorrection(PlayerPositionLookS2CPacket packet, CallbackInfo info) {
		FlagDetector.flag();
	}
}
