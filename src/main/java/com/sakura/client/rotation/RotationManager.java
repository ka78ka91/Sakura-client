package com.sakura.client.rotation;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Turns the player toward an angle over several ticks instead of snapping there.
 *
 * <p>Modelled on LiquidBounce's {@code RotationManager} (GPL-3.0): a module asks for an angle every tick it wants
 * to aim, and the manager walks the view toward it using the module's own smoothing. When the module stops asking
 * the manager keeps aiming for {@code TicksUntilReset} ticks and then lets go, so an intermittent target does not
 * make the view twitch back and forth.</p>
 *
 * <h2>Silent rotation without touching the packet</h2>
 *
 * <p>LiquidBounce builds silent rotations by rewriting the outgoing movement packet and then has to correct the
 * player's movement direction afterwards, because the game computed that movement from the spoofed angle. This
 * implementation instead swaps the angles in only for the duration of
 * {@code ClientPlayerEntity#sendMovementPackets} and puts them back on the way out. The packet therefore carries
 * the module's angle while everything else —movement, camera, the player's own input —keeps using the real one,
 * and no movement correction is needed at all.</p>
 */
public final class RotationManager {

	/** Below this the turn would never finish, so it doubles as a floor on the speed setting. */
	private static final float MIN_SPEED = 0.05f;

	/**
	 * Reaction delay before a new target is followed, in milliseconds.
	 *
	 * <p>A person does not start moving the instant something enters their crosshair; they notice, and then they
	 * move. The delay is derived from the configured turn speed so it stays orthogonal to it: a module set to
	 * snap waits {@link #REACTION_MIN_MILLIS}, and one set to crawl waits {@link #REACTION_MAX_MILLIS}. Adding a
	 * setting for it would mean every rotating module grew a new parameter, and the speed the player already
	 * chose is a fair proxy for how mechanical they want the aim to feel.</p>
	 */
	private static final long REACTION_MIN_MILLIS = 100L;
	private static final long REACTION_MAX_MILLIS = 250L;
	/** Turn speed at or above which the reaction is immediate, and at or below which it is at its longest. */
	private static final float REACTION_FAST_SPEED = 60.0f;
	private static final float REACTION_SLOW_SPEED = 5.0f;

	/**
	 * Ceiling on the small random offset left in the aimed angle, in degrees.
	 *
	 * <p>A turn that lands on the same angle to three decimal places every time is a signature in itself: a real
	 * hand always overshoots or undershoots a little. The offset is drawn fresh whenever it is applied and
	 * scaled down for a module whose configured speed is already slow enough to look deliberate.</p>
	 */
	private static final float MAX_JITTER_DEGREES = 2.0f;

	private static Rotation current;
	private static Rotation target;
	private static RotationSettings settings;
	private static Rotation startRotation;
	private static Rotation silentRotation;
	private static Rotation lastSent;

	private static float startYawDelta;
	private static float startPitchDelta;
	private static int elapsed;
	private static int ticksForTurn = 1;
	private static int ticksSinceRequest = Integer.MAX_VALUE;
	/** Wall-clock moment a new target was taken on, or {@code 0} while no turn is waiting out its reaction. */
	private static long targetAcquiredAt;
	/** Whether the reaction delay for the current target has already elapsed. */
	private static boolean reactionElapsed;
	/** Module-supplied reaction delay, or {@code -1} to derive it from the turn speed. */
	private static long reactionOverrideMillis = -1L;

	private static Float savedYaw;
	private static Float savedPitch;

	private RotationManager() {
	}

	/**
	 * Asks the view to look at an angle. Call it every tick while aiming.
	 *
	 * @param player    the player whose view is being turned
	 * @param yaw       wanted yaw in degrees
	 * @param pitch     wanted pitch in degrees
	 * @param newSettings the calling module's rotation settings
	 */
	public static void request(PlayerEntity player, float yaw, float pitch, RotationSettings newSettings) {
		Rotation wanted = new Rotation(yaw, pitch).clamped();

		if (current == null) {
			current = new Rotation(player.getYaw(), player.getPitch());
		}

		boolean newTarget = target == null || target.yaw() != wanted.yaw() || target.pitch() != wanted.pitch();

		target = wanted;
		settings = newSettings;
		ticksSinceRequest = 0;

		if (newTarget) {
			startRotation = current;
			elapsed = 0;
			reactionElapsed = false;
			startYawDelta = MathHelper.wrapDegrees(wanted.yaw() - current.yaw());
			startPitchDelta = wanted.pitch() - current.pitch();

			float speed = Math.max(MIN_SPEED, newSettings.speed());
			float worst = Math.max(Math.abs(startYawDelta), Math.abs(startPitchDelta));
			ticksForTurn = Math.max(1, (int) Math.ceil(worst / speed));
		}
	}

	/** Advances the turn and applies it. Called once per client tick. */
	public static void tick(PlayerEntity player) {
		if (player == null || target == null || current == null || settings == null) {
			return;
		}

		if (ticksSinceRequest != Integer.MAX_VALUE) {
			ticksSinceRequest++;
		}

		if (ticksSinceRequest > settings.ticksUntilReset()) {
			reset();
			return;
		}

		// The reaction window: ticks spent noticing the target rather than tracking it. `elapsed` doubles as the
		// counter because it is already reset on every new target, and it is handed back to the ease at zero so
		// the turn still starts from its beginning.
		if (!reactionElapsed) {
			elapsed++;

			if (elapsed * 50L < reactionDelayMillis()) {
				return;
			}

			reactionElapsed = true;
			elapsed = 0;
		}

		advance();
		apply(player);
	}

	/**
	 * @param speed the module's configured degrees per tick
	 * @return how long to wait before starting to follow a new target, in milliseconds
	 */
	private static long derivedReactionMillis(float speed) {
		float span = REACTION_FAST_SPEED - REACTION_SLOW_SPEED;
		float factor = span <= 0.0f ? 0.0f : (REACTION_FAST_SPEED - speed) / span;
		factor = Math.clamp(factor, 0.0f, 1.0f);
		return Math.round(REACTION_MIN_MILLIS + (REACTION_MAX_MILLIS - REACTION_MIN_MILLIS) * factor);
	}

	/**
	 * Lets the calling module supply its own reaction delay instead of the derived one.
	 *
	 * <p>A module that exposes the delay as a setting has to be able to win, otherwise its parameter would be
	 * silently added to a number the manager invented from the turn speed. Modules without such a setting simply
	 * never call this and get the derived value.</p>
	 *
	 * @param millis the delay to use, or a negative value to go back to deriving it from the turn speed
	 */
	public static void setReactionOverride(long millis) {
		reactionOverrideMillis = millis < 0L ? -1L : millis;
	}

	private static long reactionDelayMillis() {
		return reactionOverrideMillis >= 0L ? reactionOverrideMillis : derivedReactionMillis(settings.speed());
	}

	/**
	 * @param speed the module's configured degrees per tick
	 * @return the largest offset this module's aim may carry, in degrees
	 */
	private static float jitterDegrees(float speed) {
		float factor = Math.clamp(speed / REACTION_FAST_SPEED, 0.0f, 1.0f);
		return MAX_JITTER_DEGREES * factor;
	}

	private static void advance() {
		float speed = Math.max(MIN_SPEED, settings.speed());
		float yaw;
		float pitch;

		if (settings.mode() == RotationMode.LINEAR) {
			float remainingYaw = MathHelper.wrapDegrees(target.yaw() - current.yaw());
			float remainingPitch = target.pitch() - current.pitch();
			yaw = current.yaw() + Math.copySign(Math.min(speed, Math.abs(remainingYaw)), remainingYaw);
			pitch = current.pitch() + Math.copySign(Math.min(speed, Math.abs(remainingPitch)), remainingPitch);
		} else {
			elapsed++;
			double progress = Math.min(1.0, (double) elapsed / ticksForTurn);
			double eased = settings.mode().ease(progress);
			// Position along the curve is measured from where the turn started, so rounding cannot make it drift.
			yaw = startRotation.yaw() + (float) (startYawDelta * eased);
			pitch = startRotation.pitch() + (float) (startPitchDelta * eased);
		}

		if (Math.abs(MathHelper.wrapDegrees(target.yaw() - yaw)) <= settings.resetThreshold()) {
			yaw = target.yaw();
		}

		if (Math.abs(target.pitch() - pitch) <= settings.resetThreshold()) {
			pitch = target.pitch();
		}

		current = new Rotation(yaw, pitch).clamped();
	}

	private static void apply(PlayerEntity player) {
		Rotation aimed = jittered();

		if (settings.silent()) {
			// Held back for the packet swap; the camera stays where the player put it.
			silentRotation = aimed;
			return;
		}

		silentRotation = null;
		player.setYaw(aimed.yaw());
		player.setPitch(aimed.pitch());
	}

	/**
	 * @return the current aim with a small fresh offset, which is what keeps the angle from being identical
	 *         every time the module settles on the same target
	 *
	 * <p>Redrawn on every call rather than held for the duration of a turn: a constant offset would just be a
	 * different constant. The offset is bounded by {@link #jitterDegrees(float)} and the result is clamped to
	 * legal angles, so it can never point the pitch past straight up or down.</p>
	 */
	private static Rotation jittered() {
		float bound = jitterDegrees(settings.speed());

		if (bound <= 0.0f) {
			return current;
		}

		double yawOffset = ThreadLocalRandom.current().nextDouble(-bound, bound);
		double pitchOffset = ThreadLocalRandom.current().nextDouble(-bound, bound);
		return new Rotation(current.yaw() + (float) yawOffset, current.pitch() + (float) pitchOffset).clamped();
	}

	/**
	 * Stops aiming.
	 *
	 * <p>A visible rotation is left where it ended rather than snapped back: the player's own mouse takes over
	 * from there, and snapping would undo whatever they did while the module was aiming.</p>
	 */
	public static void reset() {
		target = null;
		settings = null;
		current = null;
		startRotation = null;
		silentRotation = null;
		ticksSinceRequest = Integer.MAX_VALUE;
		// A fresh episode owes its own reaction; without this the next target would start moving immediately.
		reactionElapsed = false;
		// The module that asked for an override has stopped aiming, so the next one gets the derived delay.
		reactionOverrideMillis = -1L;
	}

	/** Called from the mixin at the head of {@code ClientPlayerEntity#sendMovementPackets}. */
	public static void beginPacket(PlayerEntity player) {
		// Never leave a stale swap behind if a previous send did not return normally.
		endPacket(player);

		if (silentRotation == null) {
			return;
		}

		savedYaw = player.getYaw();
		savedPitch = player.getPitch();
		player.setYaw(silentRotation.yaw());
		player.setPitch(silentRotation.pitch());
		lastSent = silentRotation;
	}

	/** Called from the mixin on the way out of {@code ClientPlayerEntity#sendMovementPackets}. */
	public static void endPacket(PlayerEntity player) {
		if (savedYaw == null) {
			return;
		}

		player.setYaw(savedYaw);
		player.setPitch(savedPitch == null ? player.getPitch() : savedPitch);
		savedYaw = null;
		savedPitch = null;
	}

	/** @return true while a rotation is being aimed, either visibly or silently */
	public static boolean isActive() {
		return target != null && current != null;
	}

	/** @return the angle being aimed at, or {@code null} when idle */
	public static Rotation getRotation() {
		return current;
	}

	/** @return the angle the last movement packet carried, or {@code null} when no silent rotation was sent */
	public static Rotation getLastSentRotation() {
		return lastSent;
	}

	/** @return true when the current rotation is kept off the camera and only put into packets */
	public static boolean isSilent() {
		return settings != null && settings.silent();
	}
}
