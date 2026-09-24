package com.sakura.client.module.impl;

import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;

/**
 * Pushes the vanilla brightness (gamma) option far past its slider range while enabled, and puts the player's
 * own value back on disable.
 *
 * <p>This is a real implementation rather than a stub: it only touches a public option, so it needs no
 * mixin.</p>
 *
 * <p>{@code MAX_GAMMA} is deliberately well above the {@code 1.0} the vanilla slider stops at. That slider
 * limit is a UI constraint, not a lighting one: the option is a plain double that feeds the client's own
 * brightness curve, and the curve keeps responding to larger values, so a modest bump such as 1.0 barely
 * reads as "full bright" in a dark cave. Ten is chosen to saturate the visible range without pushing the
 * value into a region where the curve's output stops changing; the server is never told about it.</p>
 */
public class FullbrightModule extends Module {

	private static final double MAX_GAMMA = 10.0;

	private double previousGamma = 0.5;
	private boolean captured;
	private boolean pending;

	public FullbrightModule() {
		super("Fullbright", Category.VISUALS, "Maximum brightness without light sources");
	}

	@Override
	public void onEnable() {
		SimpleOption<Double> gamma = gamma();

		if (gamma == null) {
			// The client is not usable yet; apply on the next tick instead of crashing.
			this.pending = true;
			return;
		}

		this.previousGamma = gamma.getValue();
		this.captured = true;
		gamma.setValue(MAX_GAMMA);
	}

	@Override
	public void onTick() {
		if (this.pending) {
			this.pending = false;

			if (isEnabled()) {
				onEnable();
			}
		}
	}

	@Override
	public void onDisable() {
		if (this.captured) {
			SimpleOption<Double> gamma = gamma();

			if (gamma != null) {
				gamma.setValue(this.previousGamma);
			}

			this.captured = false;
		}

		this.pending = false;
	}

	/** The brightness option, or {@code null} while the client is still being constructed. */
	private static SimpleOption<Double> gamma() {
		MinecraftClient client = MinecraftClient.getInstance();

		return client == null || client.options == null ? null : client.options.getGamma();
	}
}
