package com.sakura.client.module.impl;

import com.sakura.client.mixin.GameOptionsAccessor;
import com.sakura.client.mixin.SimpleOptionAccessor;
import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;

/**
 * Pushes the vanilla brightness (gamma) option far past its slider range while enabled, and puts the player's
 * own value back on disable.
 *
 * <p>{@code MAX_GAMMA} is deliberately well above the {@code 1.0} the vanilla slider stops at. That slider
 * limit is a UI constraint, not a lighting one: the option holds a plain {@code Double} that feeds the client's
 * own brightness curve, and the curve keeps responding to larger values. A modest bump barely reads as "full
 * bright" in a dark cave, so the value is set high enough to saturate the visible range while staying well
 * below anything that could overflow the curve's arithmetic.</p>
 *
 * <h2>Why this cannot use {@code setValue}</h2>
 *
 * <p>{@code SimpleOption.setValue} runs the value through the option's validation callback, and the brightness
 * option validates against its slider range. Handing it ten threw
 * {@code IllegalArgumentException: Illegal option value 10.0 for Brightness}, which left the module with no
 * effect at all — and because the exception escaped {@code onEnable}, the brightness was never changed in the
 * first place. The value is therefore written through {@link SimpleOptionAccessor}, which stores it verbatim.
 * The visible side effect is that the Brightness slider in Video Settings jumps to its maximum while the module
 * is on, which doubles as confirmation that the module is actually doing something.</p>
 */
public class FullbrightModule extends Module {

	/** Bright enough to saturate the lightmap curve in an unlit cave, far below the range where it misbehaves. */
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

		this.previousGamma = rawValue(gamma);
		this.captured = true;
		write(gamma, MAX_GAMMA);
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
				write(gamma, this.previousGamma);
			}

			this.captured = false;
		}

		this.pending = false;
	}

	/** The brightness option, or {@code null} while the client is still being constructed. */
	@SuppressWarnings("unchecked")
	private static SimpleOption<Double> gamma() {
		MinecraftClient client = MinecraftClient.getInstance();

		if (client == null || client.options == null) {
			return null;
		}

		return ((GameOptionsAccessor) (GameOptions) client.options).sakura$getGammaOption();
	}

	/**
	 * @return the option's raw stored value
	 *
	 * <p>The mixin interface is not a supertype of {@code SimpleOption} at compile time — it is added at
	 * runtime — so the cast has to go through {@code Object}.</p>
	 */
	private static double rawValue(SimpleOption<Double> option) {
		Double stored = ((SimpleOptionAccessor<Double>) (Object) option).sakura$getRawValue();
		return stored == null ? 0.5 : stored;
	}

	/** Stores {@code value} without running the option's slider-range validation. */
	private static void write(SimpleOption<Double> option, double value) {
		((SimpleOptionAccessor<Double>) (Object) option).sakura$setRawValue(value);
	}
}
