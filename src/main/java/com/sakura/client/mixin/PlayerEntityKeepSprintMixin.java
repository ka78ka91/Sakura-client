package com.sakura.client.mixin;

import com.sakura.client.module.impl.KeepSprintModule;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Surrounds an attack with the KeepSprint module's velocity capture and restore.
 *
 * <p>Capturing here rather than reading vanilla's own argument means the module does not depend on how this
 * version reduces velocity 鈥?the values before the attack are simply put back.</p>
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityKeepSprintMixin {

	@Inject(method = "attack(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"))
	private void sakura$capturePreAttackVelocity(Entity target, CallbackInfo info) {
		KeepSprintModule.capturePreAttackVelocity((PlayerEntity) (Object) this);
	}

	@Inject(method = "attack(Lnet/minecraft/entity/Entity;)V", at = @At("TAIL"))
	private void sakura$restoreVelocity(Entity target, CallbackInfo info) {
		KeepSprintModule.restoreVelocity((PlayerEntity) (Object) this);
	}
}
