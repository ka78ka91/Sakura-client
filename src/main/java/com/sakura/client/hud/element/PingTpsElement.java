package com.sakura.client.hud.element;

import com.sakura.client.config.ConfigManager;
import com.sakura.client.hud.HudAnchor;
import com.sakura.client.hud.HudModule;
import com.sakura.client.hud.TickRateTracker;
import com.sakura.client.render.Animations;
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
 *
 * <p>Both numbers are eased toward their source value: a latency sample arrives about once a second and the
 * tick rate is measured over a one second window, so the raw values step rather than move, while the pill
 * should read as a live gauge. Each value also carries its own colour, green while healthy through amber to
 * red, so the state of the connection can be judged without reading the digits.</p>
 */
public final class PingTpsElement extends HudModule {

	private static final float PANEL_HEIGHT = 16.0f;
	private static final float PADDING_X = 6.0f;
	private static final float MIN_WIDTH = 70.0f;
	/** Room between the latency and the tick rate, where the divider sits. */
	private static final float GAP = 9.0f;
	private static final float DIVIDER_WIDTH = 1.0f;
	private static final float DIVIDER_HEIGHT = 8.0f;
	/** How quickly the displayed numbers catch up with their sources, in e-folds per second. */
	private static final float VALUE_SPEED = 5.0f;

	private static final int TEXT_DIM = 0xFF9A9AA2;
	private static final int DIVIDER = 0x33FFFFFF;
	private static final int PING_GOOD = 0xFF6BE89A;
	private static final int PING_MID = 0xFFFFC04D;
	private static final int PING_BAD = 0xFFFF5A5A;
	private static final int TPS_GOOD = 0xFF6BE89A;
	private static final int TPS_MID = 0xFFFFC04D;
	private static final int TPS_BAD = 0xFFFF5A5A;
	/** Latencies at or below the first are flawless, at or above the second are unplayable. */
	private static final float PING_BEST = 50.0f;
	private static final float PING_WORST = 250.0f;
	/** Tick rates at or above the first are healthy, at or below the second mean the server is struggling. */
	private static final float TPS_BEST = 20.0f;
	private static final float TPS_WORST = 10.0f;
	/** Width of the tick-rate scale, from a healthy twenty down to a timing-out ten. */
	private static final float TPS_SPAN = TPS_BEST - TPS_WORST;
	/** Glass body: a dark, slightly cool gradient drawn over the blurred world. */
	private static final int GLASS_TOP = 0xB414141A;
	private static final int GLASS_BOTTOM = 0x8C0A0A0F;
	private static final int GLASS_BORDER = 0x2EFFFFFF;
	private static final int GLASS_SHADOW = 0x66000000;
	private static final float GLASS_SHADOW_SPREAD = 4.0f;

	private final BooleanSetting ping = setting(new BooleanSetting("Ping",
			"Show the latency vanilla measured for this connection.", true));
	private final BooleanSetting tps = setting(new BooleanSetting("TPS",
			"Show the tick rate measured from the server's own world time.", true));
	private final BooleanSetting decimals = setting(new BooleanSetting("TPS Decimals",
			"Show one decimal on the tick rate.", true));

	private final Animations.Clock clock = new Animations.Clock();
	private float shownPing;
	private float shownTps;
	private boolean pingPrimed;
	private boolean tpsPrimed;

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

	/** The latency half of the pill, e.g. {@code Ping 43ms} or {@code Ping --} while unknown. */
	private String pingText() {
		int latency = pingOf(MinecraftClient.getInstance());

		return "Ping " + (latency < 0 ? "--" : Math.round(this.shownPing) + "ms");
	}

	/** The tick rate half of the pill, e.g. {@code TPS 20.0}. */
	private String tpsText() {
		return "TPS " + formatTicksPerSecond(this.shownTps, this.decimals.get());
	}

	private float pingTextWidth() {
		return this.ping.get() ? RenderUtils.textWidth(pingText()) : 0.0f;
	}

	private float tpsTextWidth() {
		return this.tps.get() ? RenderUtils.textWidth(tpsText()) : 0.0f;
	}

	/** True when both halves are shown, which is the only case that gets the divider between them. */
	private boolean bothShown() {
		return this.ping.get() && this.tps.get();
	}

	@Override
	public float getWidth() {
		float width = pingTextWidth() + tpsTextWidth();

		if (bothShown()) {
			width += GAP;
		}

		return Math.max(MIN_WIDTH, width + PADDING_X * 2.0f);
	}

	@Override
	public float getHeight() {
		return PANEL_HEIGHT;
	}

	@Override
	public void render(DrawContext context, float x, float y) {
		float delta = this.clock.tick();

		advancePing(delta);
		advanceTps(delta);

		float width = getWidth();

		drawGlass(context, x, y, width, PANEL_HEIGHT, cornerRadius());

		// Laid out from the right edge, the same direction the whole element is anchored in.
		float right = x + width - PADDING_X;

		if (this.tps.get()) {
			RenderUtils.drawTextVCentered(context, tpsText(), right, y, PANEL_HEIGHT,
					this.tpsPrimed ? tpsColor(this.shownTps) : TEXT_DIM, true, Align.RIGHT);
			right -= tpsTextWidth();
		}

		if (bothShown()) {
			RenderUtils.drawRoundedRect(context, right - GAP * 0.5f - DIVIDER_WIDTH * 0.5f,
					y + (PANEL_HEIGHT - DIVIDER_HEIGHT) * 0.5f, DIVIDER_WIDTH, DIVIDER_HEIGHT, 0.5f, DIVIDER);
			right -= GAP;
		}

		if (this.ping.get()) {
			int latency = pingOf(MinecraftClient.getInstance());

			RenderUtils.drawTextVCentered(context, pingText(), right, y, PANEL_HEIGHT,
					latency < 0 || !this.pingPrimed ? TEXT_DIM : pingColor(this.shownPing), true, Align.RIGHT);
		}
	}

	/** Eases the displayed latency toward the sample vanilla reports. */
	private void advancePing(float delta) {
		int latency = pingOf(MinecraftClient.getInstance());

		if (latency < 0) {
			// No reading: the last value is kept so coming back online does not snap the number.
			this.pingPrimed = false;
			return;
		}

		if (this.pingPrimed) {
			this.shownPing = Animations.approach(this.shownPing, latency, VALUE_SPEED, delta);
		} else {
			this.pingPrimed = true;
			this.shownPing = latency;
		}
	}

	/** Eases the displayed tick rate toward the measured one. */
	private void advanceTps(float delta) {
		double measured = TickRateTracker.getTicksPerSecond();

		if (Double.isNaN(measured)) {
			this.tpsPrimed = false;
			return;
		}

		if (this.tpsPrimed) {
			this.shownTps = Animations.approach(this.shownTps, (float) measured, VALUE_SPEED, delta);
		} else {
			this.tpsPrimed = true;
			this.shownTps = (float) measured;
		}
	}

	/** @return the colour of a latency reading, green when it is close to a local server and red when not */
	private static int pingColor(float latency) {
		return ramp(Animations.clamp01((PING_WORST - latency) / (PING_WORST - PING_BEST)),
				PING_GOOD, PING_MID, PING_BAD);
	}

	/** @return the colour of a tick rate, green at a full twenty and red when the server is timing out */
	private static int tpsColor(float ticksPerSecond) {
		return ramp(Animations.clamp01((ticksPerSecond - TPS_WORST) / TPS_SPAN),
				TPS_GOOD, TPS_MID, TPS_BAD);
	}

	/** Maps a 0..1 quality onto the good → mid → bad colour ramp. */
	private static int ramp(float quality, int good, int mid, int bad) {
		return quality < 0.5f
				? RenderUtils.mix(bad, mid, quality * 2.0f)
				: RenderUtils.mix(mid, good, (quality - 0.5f) * 2.0f);
	}

	/** The shared Sakura glass material: gradient body, hairline border, drop shadow and an accent wash. */
	private static void drawGlass(DrawContext context, float x, float y, float width, float height, float radius) {
		RenderUtils.drawGlassPanel(context, x, y, width, height, radius,
				GLASS_TOP, GLASS_BOTTOM, GLASS_BORDER, GLASS_SHADOW, GLASS_SHADOW_SPREAD);
		RenderUtils.drawAccentWash(context, x, y, width, height, radius, ConfigManager.get().accentColor, 1.0f);
	}
}
