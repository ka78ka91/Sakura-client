package com.sakura.client.mixin;

import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the option object behind the vanilla brightness setting.
 *
 * <p>{@code GameOptions.getGamma()} already returns the object, so this accessor exists only so a module can
 * reach it through a mixin interface like every other member the client pokes at. The interesting operation is
 * not here but in {@link SimpleOptionAccessor}: the brightness option validates its value against the slider
 * range, and Fullbright deliberately needs a value outside that range.</p>
 */
@Mixin(GameOptions.class)
public interface GameOptionsAccessor {

	/** @return the live {@code SimpleOption} backing the "Brightness" slider, never {@code null} in game */
	@Accessor("gamma")
	SimpleOption<Double> sakura$getGammaOption();
}
