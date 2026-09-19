package com.sakura.client.safety;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Counts clicks over a rolling one second window, the way LiquidBounce's {@code Clicker} and
 * {@code RollingClickArray} do.
 *
 * <p>Only timestamps inside the window are kept, so the reported CPS decays honestly when clicking stops
 * instead of staying at whatever peak was reached.</p>
 */
public final class Clicker {

	private static final long WINDOW_MILLIS = 1000L;
	private static final int CAPACITY = 128;

	private final Deque<Long> timestamps = new ArrayDeque<>();

	/** Records a click at the current time. */
	public void registerClick() {
		registerClick(System.currentTimeMillis());
	}

	/**
	 * Records a click at a given time.
	 *
	 * <p>Exposed so the rolling window can be driven deterministically. Timestamps must be non-decreasing, which
	 * the no-argument overload guarantees because {@code System.currentTimeMillis()} only moves forward;
	 * {@link #prune(long)} relies on the oldest click sitting at the head of the queue.</p>
	 */
	public void registerClick(long millis) {
		this.timestamps.addLast(millis);

		while (this.timestamps.size() > CAPACITY) {
			this.timestamps.removeFirst();
		}
	}

	/** Drops timestamps that have fallen out of the window. */
	public void prune() {
		prune(System.currentTimeMillis());
	}

	public void prune(long now) {
		while (!this.timestamps.isEmpty() && now - this.timestamps.peekFirst() > WINDOW_MILLIS) {
			this.timestamps.removeFirst();
		}
	}

	/** @return clicks in the last second, after dropping everything older */
	public int getCps() {
		return getCps(System.currentTimeMillis());
	}

	public int getCps(long now) {
		prune(now);
		return this.timestamps.size();
	}

	/** @return the gap since the previous click in milliseconds, or {@code -1} when there is no previous click */
	public long getLastGap(long now) {
		prune(now);

		if (this.timestamps.size() < 2) {
			return -1L;
		}

		Long[] values = this.timestamps.toArray(new Long[0]);
		return values[values.length - 1] - values[values.length - 2];
	}

	public void reset() {
		this.timestamps.clear();
	}
}
