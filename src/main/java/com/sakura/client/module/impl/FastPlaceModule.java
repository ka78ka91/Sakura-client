package com.sakura.client.module.impl;

import com.sakura.client.mixin.MinecraftClientAccessorCooldown;
import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import com.sakura.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;

/**
 * Shortens the pause between two item uses.
 *
 * <p>Vanilla leaves four ticks between placements, which is what stops a player from emptying a stack of blocks
 * in a single second. This writes that counter down, so the module is rated risky and off by default: the server
 * receives the same placement packets, but it receives them faster than an unmodified client can send them, and
 * that rate is exactly what a placement check measures.</p>
 *
 * <p>The write goes to the same field vanilla reads, so nothing is bypassed — every placement still travels
 * through the interaction manager and is still validated server-side. Only the client's own pacing changes.</p>
 */
public final class FastPlaceModule extends Module {

	private final NumberSetting cooldown = setting(new NumberSetting("Cooldown",
			"Ticks to wait between item uses. Vanilla uses 4.", 0.0, 0.0, 4.0, 1.0, "t"));

	public FastPlaceModule() {
		super("FastPlace", Category.PLAYER, "Places blocks with less delay between them.");
	}

	@Override
	public void onTick() {
		if (!isEnabled()) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();

		if (client.player == null || client.currentScreen != null) {
			return;
		}

		// Only ever shorten: if vanilla is already waiting longer than the configured value — it is not, but a
		// future version might — the module must not push the counter back up.
		int wanted = this.cooldown.intValue();
		MinecraftClientAccessorCooldown accessor = (MinecraftClientAccessorCooldown) (Object) client;

		if (accessor.sakura$getItemUseCooldown() > wanted) {
			accessor.sakura$setItemUseCooldown(wanted);
		}
	}
}
