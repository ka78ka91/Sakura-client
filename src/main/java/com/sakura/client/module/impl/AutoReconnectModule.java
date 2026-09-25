package com.sakura.client.module.impl;

import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import com.sakura.client.setting.BooleanSetting;
import com.sakura.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/**
 * Reconnects to the server after being disconnected.
 *
 * <p>The disconnect is noticed through Fabric's own {@code ClientPlayConnectionEvents.DISCONNECT}, and the
 * reconnect goes through vanilla's {@code ConnectScreen.connect} — the same entry point the multiplayer menu
 * uses. Nothing about the connection is rewritten: the client performs an ordinary login a moment after losing
 * one, which is what a player pressing "reconnect" does.</p>
 *
 * <p>It waits on the multiplayer screen rather than on the title screen, and it stops after the configured
 * number of attempts, so a server that is down does not turn into an endless reconnect loop hammering it — that
 * is both rude and a very recognisable pattern.</p>
 *
 * <p>The server to return to is remembered from the connection that was just lost, so a manual disconnect from
 * one server and a later kick from another cannot send the player somewhere they did not choose.</p>
 */
public final class AutoReconnectModule extends Module {

	private final NumberSetting delay = setting(new NumberSetting("Delay",
			"How long to wait after being disconnected before reconnecting.", 3000.0, 500.0, 30000.0, 500.0,
			"ms"));
	private final NumberSetting attempts = setting(new NumberSetting("Attempts",
			"How many times to try before giving up and leaving you on the menu.", 3.0, 1.0, 20.0, 1.0, ""));
	private final BooleanSetting onlyKicks = setting(new BooleanSetting("Only Kicks",
			"Do not reconnect when you left on purpose; only when the server dropped you.", true));

	private static AutoReconnectModule instance;

	/** The server whose connection was lost, or {@code null} while connected or after giving up. */
	private ServerInfo pending;
	/** When the next attempt is due, in milliseconds. */
	private long reconnectAt;
	/** Attempts made for the connection that was lost. */
	private int used;

	public AutoReconnectModule() {
		super("AutoReconnect", Category.MISC, "Reconnects you after the server drops the connection.");

		instance = this;
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnected(client));
	}

	@Override
	public void onDisable() {
		forget();
	}

	/** Records where we were so a later tick can go back, subject to the Only Kicks setting. */
	private static void onDisconnected(MinecraftClient client) {
		AutoReconnectModule module = instance;

		if (module == null || !module.isEnabled()) {
			return;
		}

		ServerInfo server = client.getCurrentServerEntry();

		if (server == null || server.address == null || server.address.isBlank()) {
			// Single-player, a realm, or a connection that never completed: nothing to reconnect to.
			module.forget();
			return;
		}

		if (module.onlyKicks.get() && client.currentScreen instanceof TitleScreen) {
			// The player quit to the title screen themselves, which is not a kick.
			module.forget();
			return;
		}

		module.pending = server;
		module.used = 0;
		module.reconnectAt = System.currentTimeMillis() + module.delay.intValue();
	}

	@Override
	public void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();

		if (!isEnabled() || this.pending == null) {
			return;
		}

		if (System.currentTimeMillis() < this.reconnectAt) {
			return;
		}

		if (this.used >= this.attempts.intValue()) {
			// Out of attempts: leave the player on the menu rather than looping forever against a dead server.
			forget();
			return;
		}

		// Only from the multiplayer screen: connecting from anywhere else would yank the player out of a screen
		// they deliberately opened, and during the disconnect itself the client is still tearing down.
		if (!(client.currentScreen instanceof MultiplayerScreen)) {
			return;
		}

		ServerInfo target = this.pending;
		this.used++;
		this.reconnectAt = System.currentTimeMillis() + this.delay.intValue();

		ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(target.address), target, false,
				null);
	}

	private void forget() {
		this.pending = null;
		this.used = 0;
		this.reconnectAt = 0L;
	}
}
