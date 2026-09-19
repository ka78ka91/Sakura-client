package com.sakura.client.hud.element;

import com.sakura.client.hud.HudAnchor;
import com.sakura.client.hud.HudModule;
import com.sakura.client.hud.TickRateTracker;
import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.RenderUtils.Align;
import com.sakura.client.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;

import java.util.Locale;

/**
 * Shows the connection's latency and the server's tick rate.
 *
 * <p>Ported from LiquidBounce's ping and TPS HUD elements (GPL-3.0), which this client keeps as one element
 * with a switch per value.</p>
 *
 * <p>The latency is whatever vanilla measured for this player, so it is a read-out rather than something this
 * client computes. The tick rate is measured from the game time the server reports, see
 * {@link TickRateTracker} — that is the achieved rate, not the rate the server is configured for.</p>
 */
public final class PingTpsElement extends HudModule {

	private static final float PANEL_HEIGHT = 16.0f;
	private static final float PADDING_X = 6.0f;
	private static final float MIN_WIDTH = 70.0f;
	private static final int PANEL_BG = 0x66000000;

	private final BooleanSetting ping = setting(new BooleanSetting("Ping",
			"Show the latency vanilla measured for this connection.", true));
	private final BooleanSetting tps = setting(new BooleanSetting("TPS",
			"Show the tick rate measured from the server's own world time.", true));
	private final BooleanSetting decimals = setting(new BooleanSetting("TPS Decimals",
			"Show one decimal on the tick rate.", true));

	public PingTpsElement() {
		super("Ping & TPS", "Latency to the server and its tick rate", HudAnchor.TOP_RIGHT, 4.0f, 40.0f, false);
	}

	/**
	 * @return the latency vanilla reports for the local player in milliseconds, or {@code -1} when it is not
	 * known yet (no connection, or the player list has not arrived)
	 */
	public static int pingOf(MinecraftClient client) {
		ClientPlayNetworkHandler handler = client.getNetworkHandler();

		if (client.player == null || handler == null) {
			return -1;
		}

		PlayerListEntry entry = handler.getPlayerListEntry(client.player.getUuid());

		return entry == null ? -1 : entry.getLatency();
	}

	/** @return the tick rate as displayed, {@code "--"} while nothing has been measured */
	public static String formatTicksPerSecond(double ticksPerSecond, boolean decimals) {
		if (Double.isNaN(ticksPerSecond)) {
			return "--";
		}

		return decimals
				? String.format(Locale.ROOT, "%.1f", ticksPerSecond)
				: String.format(Locale.ROOT, "%.0f", ticksPerSecond);
	}

	private String label() {
		StringBuilder text = new StringBuilder();

		if (this.ping.get()) {
			MinecraftClient client = MinecraftClient.getInstance();
			int latency = pingOf(client);
			text.append("Ping ").append(latency < 0 ? "--" : latency + "ms");
		}

		if (this.tps.get()) {
			if (text.length() > 0) {
				text.append("  ");
			}

			text.append("TPS ").append(formatTicksPerSecond(TickRateTracker.getTicksPerSecond(),
					this.decimals.get()));
		}

		return text.toString();
	}

	@Override
	public float getWidth() {
		String text = label();

		return Math.max(MIN_WIDTH, RenderUtils.textWidth(text) + PADDING_X * 2.0f);
	}

	@Override
	public float getHeight() {
		return PANEL_HEIGHT;
	}

	@Override
	public void render(DrawContext context, float x, float y) {
		String text = label();

		if (text.isEmpty()) {
			return;
		}

		RenderUtils.drawRoundedRect(context, x, y, getWidth(), PANEL_HEIGHT, cornerRadius(), PANEL_BG);
		RenderUtils.drawTextVCentered(context, text, x + getWidth() - PADDING_X, y, PANEL_HEIGHT,
				0xFFFFFFFF, true, Align.RIGHT);
	}
}
