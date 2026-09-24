package com.sakura.client.render;

/**
 * Temporary diagnostic switches for the glass panel's draw path.
 *
 * <p>Added while the performance of the HUD is being measured. The glass material is by far the most expensive
 * thing the client draws, and two independent changes were made to it at once: the gradient stopped being built
 * from per-scanline fills and became GPU-interpolated quads, and the shadow stack was capped at two layers.
 * Those have to be separable to know which one a frame-rate change came from.</p>
 *
 * <p>Both properties are read once at class load:</p>
 *
 * <ul>
 *     <li>{@code -Dsakura.debug.gradient=legacy} restores the pre-optimisation algorithm exactly — the
 *     per-scanline gradient and the uncapped shadow stack — so the earlier frame rate can be re-measured
 *     without checking out an older commit.</li>
 *     <li>{@code -Dsakura.debug.gradient.bands=N} overrides the gradient band count, to trade corner smoothness
 *     against fill count while measuring.</li>
 * </ul>
 *
 * <p>Delete this class, and the branches that read it in {@link RenderUtils}, once the numbers are in.</p>
 */
final class GradientDiagnostics {

	/** When set to {@code legacy}, {@link RenderUtils} reproduces its pre-optimisation behaviour. */
	static final boolean LEGACY = "legacy".equalsIgnoreCase(System.getProperty("sakura.debug.gradient", ""));

	/** Bands the new glass gradient is built from; the legacy path uses the original count. */
	static final int GLASS_GRADIENT_BANDS = readBands();

	private GradientDiagnostics() {
	}

	private static int readBands() {
		String raw = System.getProperty("sakura.debug.gradient.bands", "");

		if (raw.isEmpty()) {
			return 8;
		}

		try {
			// Clamped rather than validated: any count below one would leave the panel unpainted.
			return Math.max(1, Math.min(64, Integer.parseInt(raw.trim())));
		} catch (NumberFormatException exception) {
			return 8;
		}
	}
}
