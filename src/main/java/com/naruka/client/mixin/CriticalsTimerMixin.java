package com.naruka.client.mixin;

import com.naruka.client.module.impl.CriticalsModule;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Timer mode: slows the client's own clock for as long as a critical hit is still wanted.
 *
 * <p>{@code beginRenderTick} is handed the wall clock and turns the elapsed time into ticks, so scaling that
 * elapsed time is the one honest place to change the tick rate. The module tracks the real timestamp and the
 * scaled one so the clock never jumps backwards when the speed changes.</p>
 */
@Mixin(RenderTickCounter.Dynamic.class)
public abstract class CriticalsTimerMixin {

	@ModifyVariable(method = "beginRenderTick(JZ)I", at = @At("HEAD"), argsOnly = true, index = 1)
	private long naruka$scaleTimerTime(long timeMillis) {
		return CriticalsModule.scaleTimerTime(timeMillis);
	}
}
