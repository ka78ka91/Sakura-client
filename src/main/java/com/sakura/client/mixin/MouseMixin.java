package com.sakura.client.mixin;

import com.sakura.client.module.impl.AimbotModule;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the Aimbot when the player moved the mouse themselves.
 *
 * <p>The hook is on {@link Mouse#tick()}, which Minecraft calls once per client tick. The per-event cursor
 * callback would be finer grained, but it carries the movement in private fields, while {@code tick} lets the
 * whole thing be read through the public {@link Mouse#getX()} and {@link Mouse#getY()}. Comparing those against
 * the previous tick is exactly the resolution the aim needs: it decides once per tick whether to yield, so
 * movement that arrives and is consumed inside one tick should still count as one yield, not as several.</p>
 *
 * <p>Nothing here reads or writes the rotation. It only publishes "the player is steering right now" to the
 * module, which is what stops the aim from fighting a hand that is already moving the camera.</p>
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {

	/** Last position seen, so the delta is per tick. Starts as NaN so the first tick never counts as movement. */
	private double sakura$lastX = Double.NaN;
	private double sakura$lastY = Double.NaN;

	@Inject(method = "tick()V", at = @At("HEAD"))
	private void sakura$trackManualMovement(CallbackInfo info) {
		Mouse mouse = (Mouse) (Object) this;
		double x = mouse.getX();
		double y = mouse.getY();

		double previousX = this.sakura$lastX;
		double previousY = this.sakura$lastY;
		this.sakura$lastX = x;
		this.sakura$lastY = y;

		if (Double.isNaN(previousX) || Double.isNaN(previousY)) {
			return;
		}

		if (x != previousX || y != previousY) {
			AimbotModule.onManualMouseMove();
		}
	}
}
