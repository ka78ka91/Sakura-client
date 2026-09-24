package com.sakura.client.render;

/**
 * Temporary diagnostic switch for the shadow stack, left over from the HUD performance work.
 *
 * <p>The glass body no longer has a gradient, so the band-count override that used to live here is gone; what
 * remains is the way to reproduce the uncapped shadow stack for a comparison run.</p>
 *
 * <p>{@code -Dsakura.debug.gradient=legacy} restores the pre-optimisation shadow. Delete this class, the hook in
 * {@link RenderUtils#drawSoftShadow}, and the {@code legacy} branch there once the numbers are in.</p>
 */
final class GradientDiagnostics {

	/** When set to {@code legacy}, {@link RenderUtils} reproduces its pre-optimisation shadow behaviour. */
	static final boolean LEGACY = "legacy".equalsIgnoreCase(System.getProperty("sakura.debug.gradient", ""));

	private GradientDiagnostics() {
	}
}
