package com.sakura.client.hud.element;

import com.sakura.client.config.ConfigManager;
import com.sakura.client.hud.HudAnchor;
import com.sakura.client.hud.HudModule;
import com.sakura.client.hud.TickRateTracker;
import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.RenderUtils.Align;
import com.sakura.client.render.Theme;
import com.sakura.client.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;

/**
 * Where the player is, and how the connection to it is behaving: address, ping and server tick rate.
 *
 * <p>The ping is the server's own reported figure for this player, taken from the tab list entry, which is the
 * same number the tab list shows. The tick rate comes from {@link TickRateTracker}, which measures the server's
 * game time against the local clock rather than counting update packets, so a lagging server reads low instead
 * of reading a healthy twenty.</p>
 *
 * <p>Single-player has no address and no meaningful ping, so the element says so instead of showing zeros that
 * look like a bad connection. Each of the three read-outs can be switched off on its own.</p>
 */
public class ServerInfoElement extends HudModule {

	private static final float PANEL_HEIGHT = 16.0f;
	private static final float PADDING_X = 6.0f;
	private static final float COLUMN_GAP = 8.0f;
	private static final float MARGIN = 4.0f;
	private static final float GLASS_SHADOW_SPREAD = 4.0f;

	/** Ping at or above this reads as bad, at or below the second as good. */
	private static final int BAD_PING = 300;
	private static final int GOOD_PING = 60;
	private static final int PING_BAD = 0xFFFF5A5A;
	private static final int PING_MID = 0xFFFFC04D;
	private static final int PING_GOOD = 0xFF6BE89A;
	private static final float BAD_TPS = 15.0f;
	private static final float GOOD_TPS = 19.5f;

	private final BooleanSetting showAddress = setting(new BooleanSetting("Address",
			"Show the server you are connected to.", true));
	private final BooleanSetting showPing = setting(new BooleanSetting("Ping",
			"Show the latency the server reports for you.", true));
	private final BooleanSetting showTps = setting(new BooleanSetting("TPS",
			"Show the server's measured tick rate.", false));

	public ServerInfoElement() {
		super("ServerInfo", "Server address, ping and tick rate", HudAnchor.BOTTOM_LEFT, MARGIN, MARGIN, false);
	}

	@Override
	public float getWidth() {
		float width = PADDING_X * 2.0f + RenderUtils.textWidth(address());
		String ping = pingText();

		if (!ping.isEmpty()) {
			width += COLUMN_GAP + RenderUtils.textWidth(ping);
		}

		String tps = tpsText();

		if (!tps.isEmpty()) {
			width += COLUMN_GAP + RenderUtils.textWidth(tps);
		}

		return width;
	}

	@Override
	public float getHeight() {
		return PANEL_HEIGHT;
	}

	@Override
	public void render(DrawContext context, float x, float y) {
		float radius = cornerRadius();
		RenderUtils.drawGlassPanel(context, x, y, getWidth(), PANEL_HEIGHT, radius,
				Theme.glassTop(), Theme.glassBottom(), Theme.glassBorder(), Theme.glassShadow(), GLASS_SHADOW_SPREAD);
		RenderUtils.drawAccentWash(context, x, y, getWidth(), PANEL_HEIGHT, radius,
				ConfigManager.get().accentColor, 1.0f);

		float textY = y + (PANEL_HEIGHT - RenderUtils.fontHeight()) / 2.0f + 1.0f;
		float cursor = x + PADDING_X;

		RenderUtils.drawText(context, address(), cursor, textY, themed(Theme.text()), true);
		cursor += RenderUtils.textWidth(address());

		String ping = pingText();

		if (!ping.isEmpty()) {
			cursor += COLUMN_GAP;
			RenderUtils.drawText(context, ping, cursor, textY, themed(pingColor(pingMillis())), true);
			cursor += RenderUtils.textWidth(ping);
		}

		String tps = tpsText();

		if (!tps.isEmpty()) {
			cursor += COLUMN_GAP;
			RenderUtils.drawText(context, tps, cursor, textY, themed(tpsColor()), true);
		}
	}

	/** @return the address, the single-player label, or a placeholder while the client is connecting */
	private String address() {
		MinecraftClient client = MinecraftClient.getInstance();

		if (client.isInSingleplayer()) {
			return "Singleplayer";
		}

		ServerInfo server = client.getCurrentServerEntry();

		if (server == null) {
			return "Connecting";
		}

		if (!this.showAddress.get()) {
			return "Server";
		}

		return server.address == null || server.address.isBlank() ? server.name : server.address;
	}

	private String pingText() {
		if (!this.showPing.get()) {
			return "";
		}

		int ping = pingMillis();

		return ping < 0 ? "ping --" : ping + " ms";
	}

	private String tpsText() {
		if (!this.showTps.get()) {
			return "";
		}

		double tps = TickRateTracker.getTicksPerSecond();

		return Double.isNaN(tps) ? "tps --" : String.format(java.util.Locale.ROOT, "tps %.1f", tps);
	}

	/** @return the server's reported latency for this player, or {@code -1} when there is no connection */
	private static int pingMillis() {
		MinecraftClient client = MinecraftClient.getInstance();

		if (client.player == null || client.getNetworkHandler() == null) {
			return -1;
		}

		PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());

		return entry == null ? -1 : entry.getLatency();
	}

	/** @return green on a responsive connection, amber in the middle, red once it is clearly lagging */
	private static int pingColor(int ping) {
		if (ping < 0) {
			return Theme.textFaint();
		}

		float quality = ping <= GOOD_PING ? 1.0f
				: ping >= BAD_PING ? 0.0f
				: 1.0f - (ping - GOOD_PING) / (float) (BAD_PING - GOOD_PING);

		return quality < 0.5f
				? RenderUtils.mix(PING_BAD, PING_MID, quality * 2.0f)
				: RenderUtils.mix(PING_MID, PING_GOOD, (quality - 0.5f) * 2.0f);
	}

	private static int tpsColor() {
		double tps = TickRateTracker.getTicksPerSecond();

		if (Double.isNaN(tps)) {
			return Theme.textFaint();
		}

		float quality = (float) Math.clamp((tps - BAD_TPS) / (GOOD_TPS - BAD_TPS), 0.0, 1.0);

		return quality < 0.5f
				? RenderUtils.mix(PING_BAD, PING_MID, quality * 2.0f)
				: RenderUtils.mix(PING_MID, PING_GOOD, (quality - 0.5f) * 2.0f);
	}
}
