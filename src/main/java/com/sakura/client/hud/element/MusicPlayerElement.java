package com.sakura.client.hud.element;

import com.sakura.client.config.ConfigManager;
import com.sakura.client.hud.AlbumArt;
import com.sakura.client.hud.HudAnchor;
import com.sakura.client.hud.HudModule;
import com.sakura.client.hud.MockMusicProvider;
import com.sakura.client.hud.MusicProvider;
import com.sakura.client.hud.SmtcMusicProvider;
import com.sakura.client.render.Animations;
import com.sakura.client.render.RenderUtils;
import com.sakura.client.render.RenderUtils.Align;
import com.sakura.client.setting.BooleanSetting;
import com.sakura.client.setting.EnumSetting;
import com.sakura.client.setting.NumberSetting;
import com.sakura.client.setting.Tagged;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;

/**
 * Now-playing widget, driven by the real media session of the machine.
 *
 * <p>Data comes from a {@link MusicProvider}; on Windows that is {@link SmtcMusicProvider}, which reads the
 * system media session, so the widget shows whatever is actually playing — Spotify, NetEase Cloud Music,
 * QQ Music, a browser tab — together with its cover art. When no real source exists the demo provider keeps
 * the widget previewable.</p>
 *
 * <h2>What it draws</h2>
 *
 * <ul>
 *     <li>A glass panel with a shadow, a tinted gradient and an accent that is <em>taken from the cover art
 *     itself</em>, so the widget changes colour with the album.</li>
 *     <li>Cover art with rounded corners, or a music placeholder when there is none.</li>
 *     <li>Title (scrolling when it does not fit), artist, the owning application and elapsed/total time.</li>
 *     <li>A progress bar that interpolates between the half-second polls, so it glides instead of stepping.</li>
 *     <li>An equaliser badge while playing, a pause badge while paused.</li>
 *     <li>Transport controls on hover: previous, play/pause and next, sent back to the player.</li>
 * </ul>
 *
 * <p>Every animated quantity is driven by {@link Animations}, so the widget behaves identically at 60 and
 * at 240 frames per second, and the whole thing is written on top of the GUI API only — no GL calls.</p>
 */
public class MusicPlayerElement extends HudModule {

	// ------------------------------------------------------------------ layout (base units, before Scale)
	private static final float PADDING = 8.0f;
	private static final float ART_SIZE = 46.0f;
	private static final float ART_GAP = 10.0f;
	private static final float TEXT_COLUMN = 168.0f;
	private static final float PANEL_WIDTH = PADDING * 2.0f + ART_SIZE + ART_GAP + TEXT_COLUMN;
	private static final float PANEL_HEIGHT = ART_SIZE + PADDING * 2.0f;
	private static final float BAR_HEIGHT = 3.0f;
	private static final float ART_RADIUS = 9.0f;

	// ------------------------------------------------------------------ palette
	private static final int TINT_TOP = 0xBD16111C;
	private static final int TINT_BOTTOM = 0x9E0C0910;
	private static final int BORDER = 0x26FFFFFF;
	private static final int SHADOW = 0x5E000000;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFA79FB8;
	private static final int TEXT_FAINT = 0x8CFFFFFF;
	private static final int TRACK = 0x30FFFFFF;
	private static final int ART_PLACEHOLDER = 0x24FFFFFF;
	private static final int BADGE_BG = 0xA6000000;
	private static final int SCRIM = 0xB8000000;

	private static final float MARQUEE_SPEED = 26.0f;
	private static final float MARQUEE_HOLD = 1.1f;
	private static final float SHADOW_SPREAD = 7.0f;

	// ------------------------------------------------------------------ settings
	private final EnumSetting<Provider> source = setting(new EnumSetting<>("Source",
			"Where now-playing information comes from.", Provider.AUTOMATIC));
	private final BooleanSetting artwork = setting(new BooleanSetting("Cover Art",
			"Draw the cover art of the current track; off keeps only the placeholder tile.", true));
	private final BooleanSetting artist = setting(new BooleanSetting("Artist",
			"Show the artist line.", true));
	private final BooleanSetting progress = setting(new BooleanSetting("Progress",
			"Show the progress bar and the timestamps.", true));
	private final BooleanSetting sourceApp = setting(new BooleanSetting("Source App",
			"Show which application is playing.", true));
	private final BooleanSetting accentFromArt = setting(new BooleanSetting("Accent From Cover",
			"Tint the panel, the bar and the controls with a colour taken from the cover art.", true));
	private final BooleanSetting controls = setting(new BooleanSetting("Controls",
			"Show transport controls while the widget is hovered; clicking them drives the player.", true));
	private final NumberSetting scale = setting(new NumberSetting("Scale",
			"Overall size of the widget.", 1.0, 0.75, 1.5, 0.05, "x"));

	// ------------------------------------------------------------------ state
	private final Animations.Clock clock = new Animations.Clock();
	private final AlbumArt art = new AlbumArt();

	private MusicProvider provider;
	private volatile MusicProvider pending;
	private boolean connecting;
	private Provider activeSource;

	private float alpha;
	private float hover;
	private float marquee;
	private float marqueeHold = MARQUEE_HOLD;
	private boolean marqueeForward = true;
	private int accent;
	private long displayedMillis;
	private long lastReportedMillis = -1L;
	private long reportStampNanos = System.nanoTime();
	private Control pressed;
	private int pressedTicks;
	private boolean hovered;
	private float frameDelta;

	public MusicPlayerElement() {
		super("Music Player", "Now-playing widget with cover art, progress and controls",
				HudAnchor.TOP_CENTER, 0.0f, 6.0f, true);
	}

	@Override
	public float getWidth() {
		return PANEL_WIDTH * this.scale.get().floatValue();
	}

	@Override
	public float getHeight() {
		return PANEL_HEIGHT * this.scale.get().floatValue();
	}

	// ------------------------------------------------------------------ lifecycle

	/** Replaces the provider by hand; the widget otherwise picks one from its Source setting. */
	public void setProvider(MusicProvider provider) {
		closeProvider();
		this.provider = provider;
		this.activeSource = null;
		this.connecting = false;
	}

	public MusicProvider getProvider() {
		return this.provider == null ? this.pending : this.provider;
	}

	@Override
	public void onEnable() {
		this.clock.reset();
		this.alpha = 0.0f;
	}

	@Override
	public void onDisable() {
		closeProvider();
		this.art.close();
	}

	@Override
	public void onTick() {
		ensureProvider();

		if (this.pressedTicks > 0 && --this.pressedTicks == 0) {
			this.pressed = null;
		}

		MusicProvider current = this.provider;

		if (current != null) {
			current.tick();
			handleInput(current);
		} else {
			this.hovered = false;
		}
	}

	/**
	 * Starts the provider without blocking the game.
	 *
	 * <p>{@link SmtcMusicProvider#create()} waits for the bridge process to say something, which takes a
	 * moment; doing that on the client thread would freeze the game on the first frame the widget is drawn.
	 * The worker therefore builds it while the widget shows a connecting state.</p>
	 */
	private void ensureProvider() {
		Provider wanted = this.source.get();

		if (this.provider != null && this.activeSource == wanted) {
			return;
		}

		if (this.connecting && this.activeSource == wanted) {
			return;
		}

		closeProvider();
		this.activeSource = wanted;

		if (wanted == Provider.DEMO) {
			this.provider = new MockMusicProvider();
			return;
		}

		this.connecting = true;

		Thread thread = new Thread(() -> {
			MusicProvider created = SmtcMusicProvider.create();

			if (this.activeSource == wanted) {
				this.pending = created;
			} else {
				created.close();
			}
		}, "sakura-music-provider");
		thread.setDaemon(true);
		thread.start();
	}

	private void closeProvider() {
		if (this.provider != null) {
			this.provider.close();
			this.provider = null;
		}

		if (this.pending != null) {
			this.pending.close();
			this.pending = null;
		}

		this.connecting = false;
	}

	// ------------------------------------------------------------------ input

	/**
	 * Turns a click on the widget into a transport command.
	 *
	 * <p>Only runs while no screen is open, and only over the widget's own rectangle, so it can never steal
	 * a click from a menu or from the world outside it.</p>
	 */
	private void handleInput(MusicProvider current) {
		MinecraftClient client = MinecraftClient.getInstance();

		if (client.player == null || client.currentScreen != null || client.getWindow() == null) {
			this.hovered = false;
			return;
		}

		double factor = client.getWindow().getScaleFactor();
		double mouseX = client.mouse.getX() / factor;
		double mouseY = client.mouse.getY() / factor;
		int screenWidth = client.getWindow().getScaledWidth();
		int screenHeight = client.getWindow().getScaledHeight();

		if (!isHovered(mouseX, mouseY, screenWidth, screenHeight)) {
			this.hovered = false;
			return;
		}

		this.hovered = true;

		if (!this.controls.get() || !current.canControl()) {
			return;
		}

		Control hit = controlAt(mouseX - resolveX(screenWidth), mouseY - resolveY(screenHeight));

		if (hit == null) {
			return;
		}

		if (client.mouse.wasLeftButtonClicked()) {
			current.send(hit.command);
			this.pressed = hit;
			this.pressedTicks = 8;
		}
	}

	/**
	 * Which control sits under a point given in the widget's own coordinates.
	 *
	 * @param localX x relative to the widget's top-left corner, already in screen pixels
	 */
	private Control controlAt(double localX, double localY) {
		float factor = this.scale.get().floatValue();
		float x = (float) (localX / factor);
		float y = (float) (localY / factor);

		for (Control control : Control.values()) {
			if (control.contains(x, y)) {
				return control;
			}
		}

		return null;
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void render(DrawContext context, float x, float y) {
		float delta = this.clock.tick();
		float factor = this.scale.get().floatValue();

		MusicProvider current = this.provider != null ? this.provider : this.pending;

		if (current == null && this.pending != null) {
			current = this.pending;
			this.provider = this.pending;
			this.pending = null;
			this.connecting = false;
		}

		this.frameDelta = delta;
		this.alpha = Animations.approach(this.alpha, 1.0f, 7.0f, delta);
		this.hover = Animations.approach(this.hover, this.hovered ? 1.0f : 0.0f, 12.0f, delta);

		if (current != null) {
			this.art.request(current);
			this.art.upload();
		}

		updateAccent(current, delta);

		context.getMatrices().pushMatrix();
		context.getMatrices().translate(x, y);
		context.getMatrices().scale(factor, factor);

		try {
			drawPanel(context, current, delta);
		} finally {
			context.getMatrices().popMatrix();
		}
	}

	private void updateAccent(MusicProvider provider, float delta) {
		int configured = ConfigManager.get().accentColor;
		int fromArt = this.art.getAccent();
		int target = this.accentFromArt.get() && fromArt != 0 ? fromArt : configured;

		if (this.accent == 0) {
			this.accent = target;
			return;
		}

		float blend = 1.0f - (float) Math.exp(-3.0f * delta);
		this.accent = RenderUtils.mix(this.accent, target, blend);
	}

	private void drawPanel(DrawContext context, MusicProvider provider, float delta) {
		int panelAlpha = Math.round(255.0f * this.alpha);
		float radius = Math.min(cornerRadius(), PANEL_HEIGHT * 0.5f);

		if (panelAlpha <= 4) {
			return;
		}

		// Body: shadow, gradient glass, accent bleed from the cover colour.
		RenderUtils.drawGlassPanel(context, 0.0f, 0.0f, PANEL_WIDTH, PANEL_HEIGHT, radius,
				RenderUtils.multiplyAlpha(TINT_TOP, this.alpha),
				RenderUtils.multiplyAlpha(TINT_BOTTOM, this.alpha),
				RenderUtils.multiplyAlpha(BORDER, this.alpha),
				RenderUtils.multiplyAlpha(SHADOW, this.alpha), SHADOW_SPREAD);
		RenderUtils.drawAccentWash(context, 1.0f, 1.0f, PANEL_WIDTH - 2.0f, 20.0f, radius - 1.0f,
				RenderUtils.withAlpha(this.accent, Math.round(70.0f * this.alpha)),
				0.45f + 0.25f * Animations.breathe(4200L, 0.0f));

		boolean hasTrack = provider != null && provider.getTitle() != null;
		boolean available = provider == null || provider.isAvailable();

		drawArtwork(context, provider, hasTrack);
		drawText(context, provider, hasTrack, available, delta);
		drawProgressRow(context, provider, hasTrack);

		if (hasTrack && provider != null) {
			drawStateBadge(context, provider.isPlaying());
			drawControls(context, provider);
		}
	}

	private void drawArtwork(DrawContext context, MusicProvider provider, boolean hasTrack) {
		float x = PADDING;
		float y = PADDING;

		// Accent glow behind the tile, breathing slowly so the widget feels alive while music plays.
		float glow = 0.35f + 0.35f * Animations.breathe(5200L, 1.3f);

		if (hasTrack && provider != null && provider.isPlaying()) {
			RenderUtils.drawGlow(context, x, y, ART_SIZE, ART_SIZE, ART_RADIUS, 5.0f,
					RenderUtils.withAlpha(this.accent, Math.round(46.0f * glow * this.alpha)));
		}

		RenderUtils.drawRoundedRect(context, x, y, ART_SIZE, ART_SIZE, ART_RADIUS,
				RenderUtils.multiplyAlpha(ART_PLACEHOLDER, this.alpha));

		boolean drawn = hasTrack && this.artwork.get() && drawCover(context, x, y);

		if (!drawn) {
			// Placeholder tile: a music glyph on an accent-tinted gradient.
			RenderUtils.drawRoundedGradient(context, x, y, ART_SIZE, ART_SIZE, ART_RADIUS,
					RenderUtils.withAlpha(this.accent, Math.round(58.0f * this.alpha)),
					RenderUtils.withAlpha(this.accent, Math.round(20.0f * this.alpha)), 12);
			RenderUtils.drawText(context, "\u266A", x + ART_SIZE * 0.5f, y + ART_SIZE * 0.5f - 5.0f,
					RenderUtils.withAlpha(TEXT, Math.round(150.0f * this.alpha)), false, Align.CENTER);
		}

		RenderUtils.drawBorder(context, x, y, ART_SIZE, ART_SIZE, ART_RADIUS, 1.0f,
				RenderUtils.withAlpha(BORDER, Math.round(255.0f * this.alpha)));
	}

	private boolean drawCover(DrawContext context, float x, float y) {
		if (!this.art.upload()) {
			return false;
		}

		RenderUtils.drawRoundedTexture(context, this.art.getTexture(), x, y, ART_SIZE, ART_SIZE, ART_RADIUS);
		return true;
	}

	private void drawText(DrawContext context, MusicProvider provider, boolean hasTrack, boolean available,
						  float delta) {
		float textX = PADDING + ART_SIZE + ART_GAP;
		String app = this.sourceApp.get() && provider != null ? provider.getAppName() : null;
		float appWidth = app == null ? 0.0f : RenderUtils.textWidth(app) + 8.0f;

		String title;
		String subtitle;
		int titleColor;

		if (!hasTrack) {
			title = available ? "Nothing playing" : "Media session unavailable";
			subtitle = available ? "Start music in any player" : "Restart the game to retry";
			titleColor = RenderUtils.withAlpha(TEXT_DIM, Math.round(255.0f * this.alpha));
		} else {
			title = provider.getTitle();
			subtitle = this.artist.get() ? provider.getArtist() : null;
			titleColor = RenderUtils.withAlpha(TEXT, Math.round(255.0f * this.alpha));
		}

		float titleWidth = TEXT_COLUMN - appWidth;
		advanceMarquee(delta, title, titleWidth, hasTrack);

		RenderUtils.drawMarqueeText(context, title, textX, PADDING + 2.0f, titleWidth, this.marquee,
				titleColor, true);

		if (app != null) {
			RenderUtils.drawText(context, app, PANEL_WIDTH - PADDING, PADDING + 2.0f,
					RenderUtils.withAlpha(TEXT_FAINT, Math.round(210.0f * this.alpha)), false, Align.RIGHT);
		}

		if (subtitle != null && !subtitle.isEmpty()) {
			RenderUtils.drawText(context, RenderUtils.trimToWidth(subtitle, TEXT_COLUMN), textX,
					PADDING + 14.0f,
					RenderUtils.withAlpha(TEXT_DIM, Math.round(235.0f * this.alpha)), false);
		}
	}

	private void drawProgressRow(DrawContext context, MusicProvider provider, boolean hasTrack) {
		if (!this.progress.get() || provider == null || !hasTrack) {
			return;
		}

		float textX = PADDING + ART_SIZE + ART_GAP;
		float barY = PANEL_HEIGHT - PADDING - BAR_HEIGHT;

		long live = liveMillis(provider);

		if (Math.abs(live - this.displayedMillis) > 4000L) {
			this.displayedMillis = live;
		} else {
			this.displayedMillis = (long) Animations.approach((float) this.displayedMillis, (float) live,
					9.0f, this.frameDelta);
		}

		long duration = provider.getDurationMillis();
		float fraction = duration > 0L
				? Math.max(0.0f, Math.min(1.0f, this.displayedMillis / (float) duration)) : 0.0f;

		String times = formatTime(this.displayedMillis) + " / " + formatTime(duration);
		RenderUtils.drawText(context, times, PANEL_WIDTH - PADDING, barY - 12.0f,
				RenderUtils.withAlpha(TEXT_DIM, Math.round(215.0f * this.alpha)), false, Align.RIGHT);

		RenderUtils.drawProgressBar(context, textX, barY, TEXT_COLUMN, BAR_HEIGHT, fraction,
				RenderUtils.multiplyAlpha(TRACK, this.alpha),
				RenderUtils.withAlpha(this.accent, Math.round(255.0f * this.alpha)));

		// A small glowing head on the bar, so the position reads at a glance.
		if (fraction > 0.0f && fraction < 1.0f) {
			float headX = textX + TEXT_COLUMN * fraction;

			RenderUtils.drawGlow(context, headX - 2.0f, barY - 2.0f, 4.0f, BAR_HEIGHT + 4.0f, 3.0f, 4.0f,
					RenderUtils.withAlpha(this.accent, Math.round(120.0f * this.alpha)));
			RenderUtils.drawRoundedRect(context, headX - 1.5f, barY - 1.5f, 3.0f,
					BAR_HEIGHT + 3.0f, 1.5f,
					RenderUtils.withAlpha(TEXT, Math.round(240.0f * this.alpha)));
		}
	}

	/** Badge over the artwork telling playing from paused, with an equaliser that never sits still. */
	private void drawStateBadge(DrawContext context, boolean playing) {
		float size = 15.0f;
		float x = PADDING + 4.0f;
		float y = PADDING + ART_SIZE - size - 4.0f;
		int background = RenderUtils.multiplyAlpha(BADGE_BG, this.alpha);

		RenderUtils.drawRoundedRect(context, x, y, size, size, 4.5f, background);

		int colour = RenderUtils.withAlpha(TEXT, Math.round(245.0f * this.alpha));
		float centreY = y + size * 0.5f;

		if (playing) {
			float barWidth = 1.8f;
			float gap = 1.7f;
			float totalWidth = barWidth * 3.0f + gap * 2.0f;
			float startX = x + (size - totalWidth) * 0.5f;

			for (int bar = 0; bar < 3; bar++) {
				float level = 0.3f + 0.7f * Animations.breathe(760L + bar * 260L, bar * 1.4f);
				float height = 6.5f * level;

				RenderUtils.drawRoundedRect(context, startX + bar * (barWidth + gap), centreY - height * 0.5f,
						barWidth, Math.max(1.5f, height), 0.9f, colour);
			}
		} else {
			RenderUtils.drawRoundedRect(context, x + 5.0f, centreY - 3.5f, 1.8f, 7.0f, 0.9f, colour);
			RenderUtils.drawRoundedRect(context, x + 8.4f, centreY - 3.5f, 1.8f, 7.0f, 0.9f, colour);
		}
	}

	/** Transport controls: a scrim plus three buttons, faded in as the cursor arrives. */
	private void drawControls(DrawContext context, MusicProvider provider) {
		float shown = this.controls.get() && provider.canControl() ? this.hover : 0.0f;

		if (shown <= 0.02f) {
			return;
		}

		float x = PADDING;
		float y = PADDING;
		float eased = Animations.easeOutCubic(shown);

		RenderUtils.drawRoundedRect(context, x, y, ART_SIZE, ART_SIZE, ART_RADIUS,
				RenderUtils.multiplyAlpha(SCRIM, eased * this.alpha));

		for (Control control : Control.values()) {
			boolean pointed = control == this.pressed;
			int colour = RenderUtils.withAlpha(TEXT, Math.round((pointed ? 255.0f : 225.0f) * eased * this.alpha));

			if (pointed) {
				RenderUtils.drawRoundedRect(context, control.x - 1.0f, control.y - 1.0f,
						control.width + 2.0f, control.height + 2.0f, 3.0f,
						RenderUtils.withAlpha(this.accent, Math.round(120.0f * eased * this.alpha)));
			}

			drawControlIcon(context, control, colour);
		}
	}

	private void drawControlIcon(DrawContext context, Control control, int colour) {
		float centreX = control.x + control.width * 0.5f;
		float centreY = control.y + control.height * 0.5f;
		float icon = 7.0f;

		switch (control) {
			case PREVIOUS -> {
				drawTriangle(context, centreX - 0.5f, centreY, icon, colour, false);
				RenderUtils.drawRect(context, centreX - icon * 0.5f - 1.5f, centreY - icon * 0.5f, 1.4f, icon,
						colour);
			}
			case NEXT -> {
				drawTriangle(context, centreX + 0.5f, centreY, icon, colour, true);
				RenderUtils.drawRect(context, centreX + icon * 0.5f + 0.2f, centreY - icon * 0.5f, 1.4f, icon,
						colour);
			}
			case PLAY_PAUSE -> {
				drawTriangle(context, centreX + 0.6f, centreY, icon, colour, true);
			}
		}
	}

	/** Stamps a filled triangle out of 1 px columns; the GUI API has no polygon primitive. */
	private static void drawTriangle(DrawContext context, float centreX, float centreY, float size, int colour,
									 boolean pointsRight) {
		int columns = Math.max(3, Math.round(size));

		for (int column = 0; column < columns; column++) {
			float progress = (column + 0.5f) / columns;
			float taper = pointsRight ? progress : 1.0f - progress;
			float half = size * 0.5f * (1.0f - taper * 0.92f);
			float columnX = centreX + (pointsRight ? -size * 0.5f : size * 0.5f)
					+ (pointsRight ? column : -column - 1.0f);

			RenderUtils.drawRect(context, columnX, centreY - half, 1.0f, Math.max(1.0f, half * 2.0f), colour);
		}
	}

	// ------------------------------------------------------------------ helpers

	private long liveMillis(MusicProvider provider) {
		long reported = provider.getPositionMillis();

		if (provider.isPlaying()) {
			if (reported != this.lastReportedMillis) {
				this.lastReportedMillis = reported;
				this.reportStampNanos = System.nanoTime();
			}

			long elapsed = (System.nanoTime() - this.reportStampNanos) / 1_000_000L;
			return Math.max(0L, reported + elapsed);
		}

		this.lastReportedMillis = reported;
		this.reportStampNanos = System.nanoTime();
		return reported;
	}

	/** Scrolls a too-long title back and forth, pausing at both ends. */
	private void advanceMarquee(float delta, String text, float boxWidth, boolean active) {
		float overflow = RenderUtils.textWidth(text) - boxWidth;

		if (!active || overflow <= 0.0f) {
			this.marquee = Animations.approach(this.marquee, 0.0f, 8.0f, delta);
			this.marqueeForward = true;
			this.marqueeHold = MARQUEE_HOLD;
			return;
		}

		if (this.marqueeHold > 0.0f) {
			this.marqueeHold -= delta;
			return;
		}

		this.marquee += (this.marqueeForward ? MARQUEE_SPEED : -MARQUEE_SPEED) * delta;

		if (this.marquee >= overflow) {
			this.marquee = overflow;
			this.marqueeForward = false;
			this.marqueeHold = MARQUEE_HOLD;
		} else if (this.marquee <= 0.0f) {
			this.marquee = 0.0f;
			this.marqueeForward = true;
			this.marqueeHold = MARQUEE_HOLD;
		}
	}

	private static String formatTime(long millis) {
		long seconds = Math.max(0L, millis) / 1000L;
		return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
	}

	/** Where the widget's data comes from. */
	public enum Provider implements Tagged {

		/** The system media session when there is one, the demo track otherwise. */
		AUTOMATIC("Automatic"),

		/** Always the built-in demo track; useful for positioning the widget without playing anything. */
		DEMO("Demo");

		private final String label;

		Provider(String label) {
			this.label = label;
		}

		@Override
		public String getTag() {
			return this.label;
		}
	}

	/**
	 * The three transport buttons, and their hit boxes.
	 *
	 * <p>Coordinates are in the widget's own space, before the Scale setting is applied, matching the space
	 * {@link #controlAt} converts incoming mouse positions into.</p>
	 */
	private enum Control {

		PREVIOUS(MusicProvider.Command.PREVIOUS, 1.0f),
		PLAY_PAUSE(MusicProvider.Command.PLAY_PAUSE, 17.5f),
		NEXT(MusicProvider.Command.NEXT, 34.0f);

		private final MusicProvider.Command command;
		private final float x;
		private final float y = PADDING + ART_SIZE * 0.5f - 8.0f;
		private final float width = 14.0f;
		private final float height = 16.0f;

		Control(MusicProvider.Command command, float x) {
			this.command = command;
			this.x = PADDING + x;
		}

		boolean contains(float pointX, float pointY) {
			return pointX >= this.x && pointX <= this.x + this.width
					&& pointY >= this.y && pointY <= this.y + this.height;
		}
	}
}
