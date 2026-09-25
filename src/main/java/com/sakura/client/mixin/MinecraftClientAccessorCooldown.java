package com.sakura.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the delay vanilla leaves between two item uses.
 *
 * <p>{@code MinecraftClient.itemUseCooldown} is the counter behind the pause between placing a block and placing
 * the next one — four ticks in vanilla. It is private and has no setter, so FastPlace needs an accessor to write
 * it. Verified against the 1.21.11 mappings: the field exists as {@code private int itemUseCooldown}.</p>
 *
 * <p>Writing it does not skip any check: it moves the same timer vanilla reads, so the placement still goes
 * through the interaction manager and the server still validates every block.</p>
 */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessorCooldown {

	/** @return ticks still to wait before the next item use is accepted */
	@Accessor("itemUseCooldown")
	int sakura$getItemUseCooldown();

	/** Sets the wait before the next item use is accepted. Zero places again on the next tick. */
	@Accessor("itemUseCooldown")
	void sakura$setItemUseCooldown(int ticks);
}
