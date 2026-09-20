package com.sakura.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cover art for the music widget: decode, downscale, accent extraction and upload.
 *
 * <p>Three separate concerns, deliberately kept apart:</p>
 *
 * <ul>
 *     <li><b>Decoding</b> happens on a worker thread, because reading and resampling a JPEG must never touch
 *     the render thread. {@link NativeImage#read(byte[])} is pure CPU work, so it is safe off-thread.</li>
 *     <li><b>Uploading</b> happens on the render thread from {@link #upload()}, which is called once per
 *     frame while the widget draws. The texture is a fixed 256x256 so
 *     {@link NativeImageBackedTexture#setImage(NativeImage)} can be used for every following track:
 *     it closes the previous image itself and then the widget calls {@code upload()}.</li>
 *     <li><b>Accent extraction</b> also happens on the worker thread: the widget can then tint its panel and
 *     progress bar with a colour that actually belongs to the cover.</li>
 * </ul>
 */
public final class AlbumArt {

	private static final Logger LOGGER = LoggerFactory.getLogger("sakura/album-art");

	/** Square edge of the texture. Small enough to be free, large enough for a 46 px widget at 4x GUI scale. */
	private static final int TEXTURE_SIZE = 256;

	private static final Identifier TEXTURE_ID = Identifier.of("sakura", "album_art");

	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "sakura-album-art");
		thread.setDaemon(true);
		return thread;
	});

	/** Art that has been decoded and is waiting for the next render frame to be uploaded. */
	private final AtomicReference<Payload> decoded = new AtomicReference<>();

	private NativeImageBackedTexture texture;
	private int requestedGeneration = -1;
	private int accent;
	private boolean broken;

	/**
	 * Asks for the provider's current artwork. Safe to call every frame: re-decoding is keyed on the
	 * provider's generation counter, which only moves when the track's art actually changed.
	 */
	public void request(MusicProvider provider) {
		Path path = provider.getArtworkPath();
		int generation = provider.getArtworkGeneration();

		if (path == null || generation <= 0 || generation == this.requestedGeneration) {
			return;
		}

		this.requestedGeneration = generation;
		WORKER.execute(() -> decode(path, generation));
	}

	/**
	 * Uploads anything that finished decoding. Must be called from the render thread.
	 *
	 * @return true when a texture is ready to be drawn
	 */
	public boolean upload() {
		Payload payload = this.decoded.getAndSet(null);

		if (payload != null) {
			try {
				if (this.texture == null) {
					this.texture = new NativeImageBackedTexture(() -> "Sakura album art", payload.image());
					MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE_ID, this.texture);
					this.texture.upload();
				} else {
					// setImage closes the image it replaces, so nothing leaks across track changes.
					this.texture.setImage(payload.image());
					this.texture.upload();
				}

				this.accent = payload.accent();
				this.broken = false;
			} catch (RuntimeException exception) {
				LOGGER.warn("Could not upload album art: {}", exception.getMessage());
				payload.image().close();
				this.broken = true;
			}
		}

		return this.texture != null && !this.broken;
	}

	public Identifier getTexture() {
		return TEXTURE_ID;
	}

	/** A vivid colour taken from the current cover, or 0 when the cover is too grey to suggest one. */
	public int getAccent() {
		return this.accent;
	}

	/** Drops the texture; the widget calls this when it is switched off. */
	public void close() {
		this.decoded.set(null);

		if (this.texture != null) {
			try {
				MinecraftClient.getInstance().getTextureManager().destroyTexture(TEXTURE_ID);
			} catch (RuntimeException exception) {
				LOGGER.debug("Could not release the album art texture: {}", exception.getMessage());
			}

			this.texture = null;
		}

		this.accent = 0;
		this.requestedGeneration = -1;
		this.broken = false;
	}

	// ------------------------------------------------------------------ worker side

	private void decode(Path path, int generation) {
		try {
			byte[] bytes = Files.readAllBytes(path);
			NativeImage source = NativeImage.read(bytes);

			try {
				NativeImage square = resample(source);
				this.decoded.set(new Payload(square, dominantColor(square), generation));
			} finally {
				source.close();
			}
		} catch (IOException | RuntimeException exception) {
			LOGGER.warn("Could not read album art from {}: {}", path, exception.getMessage());
		}
	}

	/**
	 * Resamples the cover into a fixed square texture.
	 *
	 * <p>Box-averaged rather than nearest-neighbour, so a large cover comes out smooth instead of aliased,
	 * and stretching to a square costs nothing visually: cover art is square by definition.</p>
	 */
	private static NativeImage resample(NativeImage source) {
		int sourceWidth = Math.max(1, source.getWidth());
		int sourceHeight = Math.max(1, source.getHeight());
		NativeImage target = new NativeImage(TEXTURE_SIZE, TEXTURE_SIZE, false);

		for (int y = 0; y < TEXTURE_SIZE; y++) {
			int fromY = y * sourceHeight / TEXTURE_SIZE;
			int toY = Math.max(fromY + 1, (y + 1) * sourceHeight / TEXTURE_SIZE);

			for (int x = 0; x < TEXTURE_SIZE; x++) {
				int fromX = x * sourceWidth / TEXTURE_SIZE;
				int toX = Math.max(fromX + 1, (x + 1) * sourceWidth / TEXTURE_SIZE);

				long alpha = 0L;
				long red = 0L;
				long green = 0L;
				long blue = 0L;
				int samples = 0;

				for (int sampleY = fromY; sampleY < toY; sampleY++) {
					for (int sampleX = fromX; sampleX < toX; sampleX++) {
						int argb = source.getColorArgb(sampleX, sampleY);
						alpha += (argb >>> 24) & 0xFF;
						red += (argb >> 16) & 0xFF;
						green += (argb >> 8) & 0xFF;
						blue += argb & 0xFF;
						samples++;
					}
				}

				int divisor = Math.max(1, samples);
				target.setColorArgb(x, y, (int) (alpha / divisor) << 24 | (int) (red / divisor) << 16
						| (int) (green / divisor) << 8 | (int) (blue / divisor));
			}
		}

		return target;
	}

	/**
	 * Picks a usable accent colour out of the cover.
	 *
	 * <p>Pixels are weighted by {@code saturation * brightness} and binned by hue, so a large washed-out
	 * background cannot outvote the small vividly coloured part of the image. The winning bin is then
	 * pushed to a saturation and brightness that read well as UI chrome. A greyscale cover returns 0 and
	 * the widget falls back to the client's own accent.</p>
	 */
	private static int dominantColor(NativeImage image) {
		final int bins = 24;
		float[] weights = new float[bins];
		float[] reds = new float[bins];
		float[] greens = new float[bins];
		float[] blues = new float[bins];
		int step = Math.max(2, image.getWidth() / 64);

		for (int y = 0; y < image.getHeight(); y += step) {
			for (int x = 0; x < image.getWidth(); x += step) {
				int argb = image.getColorArgb(x, y);

				if (((argb >>> 24) & 0xFF) < 40) {
					continue;
				}

				int red = (argb >> 16) & 0xFF;
				int green = (argb >> 8) & 0xFF;
				int blue = argb & 0xFF;

				float[] hsb = toHsb(red, green, blue);

				if (hsb[1] < 0.15f || hsb[2] < 0.12f) {
					continue;
				}

				int bin = Math.min(bins - 1, (int) (hsb[0] * bins));
				float weight = hsb[1] * hsb[2];
				weights[bin] += weight;
				reds[bin] += red * weight;
				greens[bin] += green * weight;
				blues[bin] += blue * weight;
			}
		}

		int best = -1;
		float bestWeight = 0.0f;

		for (int bin = 0; bin < bins; bin++) {
			if (weights[bin] > bestWeight) {
				bestWeight = weights[bin];
				best = bin;
			}
		}

		// Nothing saturated enough to speak for the cover.
		if (best < 0 || bestWeight <= 0.0f) {
			return 0;
		}

		float red = reds[best] / bestWeight;
		float green = greens[best] / bestWeight;
		float blue = blues[best] / bestWeight;
		float[] hsb = toHsb(Math.round(red), Math.round(green), Math.round(blue));

		return fromHsb(hsb[0], Math.min(1.0f, Math.max(0.55f, hsb[1])), 0.98f);
	}

	/** @return {@code {hue 0..1, saturation 0..1, brightness 0..1}} */
	private static float[] toHsb(int red, int green, int blue) {
		float r = red / 255.0f;
		float g = green / 255.0f;
		float b = blue / 255.0f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		float hue;

		if (delta < 1.0E-6f) {
			hue = 0.0f;
		} else if (max == r) {
			hue = ((g - b) / delta) % 6.0f;
		} else if (max == g) {
			hue = (b - r) / delta + 2.0f;
		} else {
			hue = (r - g) / delta + 4.0f;
		}

		hue /= 6.0f;

		if (hue < 0.0f) {
			hue += 1.0f;
		}

		float saturation = max <= 0.0f ? 0.0f : delta / max;
		return new float[]{hue, saturation, max};
	}

	private static int fromHsb(float hue, float saturation, float brightness) {
		float h = (hue - (float) Math.floor(hue)) * 6.0f;
		int sector = (int) h;
		float fraction = h - sector;
		float p = brightness * (1.0f - saturation);
		float q = brightness * (1.0f - saturation * fraction);
		float t = brightness * (1.0f - saturation * (1.0f - fraction));

		float red;
		float green;
		float blue;

		switch (sector % 6) {
			case 0 -> {
				red = brightness;
				green = t;
				blue = p;
			}
			case 1 -> {
				red = q;
				green = brightness;
				blue = p;
			}
			case 2 -> {
				red = p;
				green = brightness;
				blue = t;
			}
			case 3 -> {
				red = p;
				green = q;
				blue = brightness;
			}
			case 4 -> {
				red = t;
				green = p;
				blue = brightness;
			}
			default -> {
				red = brightness;
				green = p;
				blue = q;
			}
		}

		return 0xFF000000 | (Math.round(red * 255.0f) << 16) | (Math.round(green * 255.0f) << 8)
				| Math.round(blue * 255.0f);
	}

	/** Decoded art on its way from the worker thread to the render thread. */
	private record Payload(NativeImage image, int accent, int generation) {
	}
}
