package com.sakura.client.module.impl;

import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import com.sakura.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Respawns the player after a configurable delay instead of waiting for the click.
 *
 * <p>Nothing is spoofed: the module calls vanilla's own {@code requestRespawn}, which is exactly what the
 * "Respawn" button on the death screen does, one tick at a time until it takes. The server sees an ordinary
 * respawn request, and the delay exists so the request does not arrive on the very first tick the screen is
 * up — a respawn that is always instant is a small but perfectly readable tell.</p>
 *
 * <p>The screen is closed as part of respawning, because {@code requestRespawn} replaces it: leaving it open
 * would mean the player keeps looking at a death screen they have already left.</p>
 */
public final class AutoRespawnModule extends Module {

	private final NumberSetting delay = setting(new NumberSetting("Delay",
			"How long to stay on the death screen before respawning.", 500.0, 0.0, 5000.0, 100.0, "ms"));

	/** When the death screen was first seen, or {@code 0} while the player is alive. */
	private long diedAt;

	public AutoRespawnModule() {
		super("AutoRespawn", Category.PLAYER, "Respawns you automatically after dying.");
	}

	@Override
	public void onDisable() {
		this.diedAt = 0L;
	}

	@Override
	public void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;

		if (!isEnabled() || player == null) {
			this.diedAt = 0L;
			return;
		}

		// The death screen is the signal, not the health value: the player is already at zero health for a
		// moment before the screen appears, and clicking through before that would fight the client.
		if (!(client.currentScreen instanceof DeathScreen)) {
			this.diedAt = 0L;
			return;
		}

		long now = System.currentTimeMillis();

		if (this.diedAt == 0L) {
			this.diedAt = now;
			return;
		}

		if (now - this.diedAt < this.delay.intValue()) {
			return;
		}

		player.requestRespawn();
		this.diedAt = 0L;
	}
}
