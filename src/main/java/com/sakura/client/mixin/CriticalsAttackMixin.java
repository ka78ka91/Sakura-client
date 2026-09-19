package com.sakura.client.mixin;

import com.sakura.client.module.impl.CriticalsModule;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives the Criticals module its say just before an attack goes out.
 *
 * <p>It is the same place LiquidBounce hooks: after the decision to attack but before anything reaches the
 * server, which is the only window where a mode can either spoof packets (Packet) or swallow the click and try
 * again once the player is falling (Jump).</p>
 */
@Mixin(MinecraftClient.class)
public abstract class CriticalsAttackMixin {

	@Inject(method = "doAttack()Z", at = @At("HEAD"), cancellable = true)
	private void sakura$criticalsBeforeAttack(CallbackInfoReturnable<Boolean> info) {
		CriticalsModule module = CriticalsModule.getInstance();

		if (module != null && module.beforeAttack()) {
			// The click is dropped on purpose; the module re-issues it once the attack would crit.
			info.setReturnValue(false);
		}
	}
}
