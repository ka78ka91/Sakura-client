package com.naruka.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the click handlers {@code MinecraftClient} keeps private.
 *
 * <p>{@code doAttack} and {@code doItemUse} are the exact paths the player's own mouse buttons take, including
 * the attack-range component check, the attack cooldown gate and the swing animation. Calling them keeps the
 * auto-clicking modules honest — they press the same button the player would, rather than reimplementing what
 * that button does and drifting from vanilla behaviour.</p>
 *
 * <p>Verified against the 1.21.11 mappings: both methods exist and are private, so an invoker is the only way to
 * reach them without reflection.</p>
 */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {

	/** @return whatever vanilla's own attack click returns: true when the attack was handled */
	@Invoker("doAttack")
	boolean naruka$doAttack();

	/** Runs vanilla's own use-item click, with all of its gates. */
	@Invoker("doItemUse")
	void naruka$doItemUse();
}
