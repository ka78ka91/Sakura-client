package com.sakura.client.mixin;

import com.sakura.client.module.impl.AutoWeaponModule;
import com.sakura.client.module.impl.NoMissCooldownModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks the miss path of an attack for the NoMissCooldown module, and the start of the attack for AutoWeapon.
 *
 * <p>In 1.21.11 {@code doAttack} ends its miss branch by calling {@code resetTicksSince} on the player, which is
 * what zeroes {@code ticksSinceLastAttack} and costs the cooldown. Verified against the 1.21.11 bytecode: the
 * miss branch calls {@code resetTicksSince()}, not the similarly named {@code resetTicksSinceLastAttack()}. The
 * redirect drops that one call; it is deliberately not an early cancel of the whole method, so the rest of the
 * attack handling stays untouched.</p>
 *
 * <p>AutoWeapon runs in the same head injector, before the attack is handed to the interaction manager, because
 * that manager sends the held-slot update first and only then the attack packet: changing the slot here is
 * enough for the server to register the hit with the new weapon, with no packet sent by hand and no second
 * attack a tick later.</p>
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientAttackMixin {

	@Inject(method = "doAttack()Z", at = @At("HEAD"), cancellable = true)
	private void sakura$beforeAttack(CallbackInfoReturnable<Boolean> info) {
		MinecraftClient client = (MinecraftClient) (Object) this;

		AutoWeaponModule.beforeAttack(client);

		if (NoMissCooldownModule.shouldCancelMissAttack(client)) {
			info.setReturnValue(false);
		}
	}

	@Redirect(method = "doAttack()Z", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/network/ClientPlayerEntity;resetTicksSince()V"))
	private void sakura$keepCooldownOnMiss(ClientPlayerEntity player) {
		if (!NoMissCooldownModule.shouldRemoveMissCooldown()) {
			player.resetTicksSince();
		}
	}
}
