package com.sakura.client.hud;

/**
 * Server tick rate, measured from the world time the server itself reports.
 *
 * <p>A client cannot count the server's ticks directly. What it does get is a world time update carrying the
 * server's game time, which advances by exactly one per server tick, so how fast that value moves against the
 * local clock is the server's achieved tick rate — lag included, and it falls to zero while the server is
 * frozen or has stopped reporting at all. Counting those packets instead would only give the vanilla sending
 * cadence, which stays at twenty no matter how badly the server is struggling.</p>
 *
 * <p>Samples are taken from the network thread and read from the render thread, so the fields are volatile
 * rather than guarded by a lock: a torn read of a {@code double} would at worst show a wrong number for one
 * frame, and a lock in the packet path would be far worse.</p>
 */
public final class TickRateTracker {

	/** A window shorter than this is dominated by packet jitter. */
	private static final long WINDOW_NANOS = 1_000_000_000L;
	/** No update for this long means the server is not ticking: frozen, paused or disconnected. */
	private static final long STALE_NANOS = 3_000_000_000L;
	/** Vanilla never ticks faster than this, so anything higher is a clock artefact, e.g. a time jump. */
	private static final double MAX_TICKS_PER_SECOND = 20.0;

	private static volatile boolean primed;
	private static volatile double ticksPerSecond = Double.NaN;
	private static long lastServerTime;
	private static long lastSampleNanos;

	private TickRateTracker() {
	}

	/** Called from the network mixin for every world time update. */
	public static void onTimeUpdate(long serverTime) {
		long now = System.nanoTime();

		if (!primed) {
			primed = true;
			lastServerTime = serverTime;
			lastSampleNanos = now;
			return;
		}

		long elapsed = now - lastSampleNanos;

		if (elapsed < WINDOW_NANOS) {
			return;
		}

		long advanced = serverTime - lastServerTime;

		// A negative delta means the client changed world or the time was set back; start over instead of
		// reporting a negative tick rate.
		ticksPerSecond = advanced >= 0L ? measure(advanced, elapsed) : Double.NaN;
		lastServerTime = serverTime;
		lastSampleNanos = now;
	}

	/**
	 * @return the measured tick rate, {@link Double#NaN} while nothing has been measured yet, and zero once the
	 * server has stopped sending time updates
	 */
	public static double getTicksPerSecond() {
		if (!primed) {
			return Double.NaN;
		}

		if (System.nanoTime() - lastSampleNanos > STALE_NANOS) {
			return 0.0;
		}

		return ticksPerSecond;
	}

	/** Drops the current sample, so the next time update starts a fresh window. */
	public static void reset() {
		primed = false;
		ticksPerSecond = Double.NaN;
	}

	/**
	 * Converts a sample into ticks per second.
	 *
	 * @param advancedTicks server ticks the reported game time moved forward
	 * @param elapsedNanos  local time the sample covers
	 */
	static double measure(long advancedTicks, long elapsedNanos) {
		if (elapsedNanos <= 0L) {
			return Double.NaN;
		}

		double measured = advancedTicks * 1.0e9 / elapsedNanos;

		return Math.max(0.0, Math.min(MAX_TICKS_PER_SECOND, measured));
	}
}
