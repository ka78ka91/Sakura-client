package com.sakura.client.safety;

/**
 * Global safety valve: remembers when the server corrected our position.
 *
 * <p>A position correction means the server disagreed with at least one movement claim the client made, so
 * anything that manipulates position, velocity or rotation packets is very likely to be the cause and very
 * likely to be checked harder for the next few ticks. Packet-manipulating modules ask
 * {@link #shouldPause()} and idle themselves out for the pause window instead of digging a deeper hole.</p>
 *
 * <p>Modelled on LiquidBounce's flag handling, where the same condition arrives as a
 * {@code ClientboundPlayerPositionPacket} event and each movement module exposes its own pause window.</p>
 */
public final class FlagDetector {

	/** LiquidBounce's Velocity module uses up to 20 ticks; 10 is the middle of that range. */
	public static final int DEFAULT_PAUSE_TICKS = 10;

	private static int pauseTicks = DEFAULT_PAUSE_TICKS;
	private static int ticksSinceFlag = Integer.MAX_VALUE;
	private static int flags;

	private FlagDetector() {
	}

	/** Records a server-side position correction. Called from the position packet handler. */
	public static void flag() {
		ticksSinceFlag = 0;
		flags++;
	}

	/** Advances the pause window. Called once per client tick. */
	public static void tick() {
		if (ticksSinceFlag != Integer.MAX_VALUE) {
			ticksSinceFlag++;
		}
	}

	/** @return true while a correction is inside the pause window */
	public static boolean isFlagged() {
		return pauseTicks > 0 && ticksSinceFlag <= pauseTicks;
	}

	/** @return true when packet-manipulating modules should hold off right now */
	public static boolean shouldPause() {
		return isFlagged();
	}

	/** @return ticks left in the pause window, zero when nothing is flagged */
	public static int getRemainingTicks() {
		if (pauseTicks <= 0 || ticksSinceFlag > pauseTicks) {
			return 0;
		}

		return pauseTicks - ticksSinceFlag;
	}

	public static int getFlagCount() {
		return flags;
	}

	public static int getPauseTicks() {
		return pauseTicks;
	}

	/** A window of zero disables the valve entirely. */
	public static void setPauseTicks(int ticks) {
		pauseTicks = Math.max(0, ticks);
	}

	public static void clear() {
		ticksSinceFlag = Integer.MAX_VALUE;
		flags = 0;
	}
}
