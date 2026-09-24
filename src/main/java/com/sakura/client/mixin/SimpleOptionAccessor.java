package com.sakura.client.mixin;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Writes an option's value without running its validation callbacks.
 *
 * <p>{@code SimpleOption.setValue} runs the value through {@code Callbacks.validate}, which for a slider is its
 * own range. That range is a UI constraint, not a gameplay one, and Fullbright needs brightness values above
 * the slider maximum — the vanilla option is a plain {@code Double}, and the lightmap reads it directly, so a
 * larger number is meaningful even though the slider cannot express it. Going through this accessor is what
 * makes that possible; the alternative would be to leave the option alone and intercept the lightmap instead,
 * which is a far heavier change for the same effect.</p>
 *
 * <p>Callers are responsible for putting an in-range value back when they are done, and for not handing this
 * method anything the option could not sensibly hold.</p>
 */
@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor<T> {

	/** @return the raw stored value, which may sit outside the option's own slider range */
	@Accessor("value")
	T sakura$getRawValue();

	/** Stores {@code value} verbatim, bypassing {@code Callbacks.validate}. */
	@Accessor("value")
	void sakura$setRawValue(T value);
}
