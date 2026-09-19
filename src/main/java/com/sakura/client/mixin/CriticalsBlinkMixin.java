package com.sakura.client.mixin;

import com.sakura.client.module.impl.CriticalsModule;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blink mode: holds back outgoing packets for a moment so the server sees the player standing still while the
 * client keeps falling.
 *
 * <p>Injected on the single point every outgoing packet passes through, and cancelled there. The module keeps the
 * packets and sends them later, unchanged.</p>
 */
@Mixin(ClientConnection.class)
public abstract class CriticalsBlinkMixin {

	@Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
	private void sakura$queueWhileBlinking(Packet<?> packet, CallbackInfo info) {
		if (CriticalsModule.shouldQueuePacket(packet)) {
			info.cancel();
		}
	}
}
