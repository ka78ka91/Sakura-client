package com.naruka.client.rotation;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

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
 * the module's angle while everything else — movement, camera, the player's own input — keeps using the real one,
 * and no movement correction is needed at all.</p>
 */
public final class RotationManager {

	/** Below this the turn would never finish, so it doubles as a floor on the speed setting. */
	private static final float MIN_SPEED = 0.05f;

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

		advance();
		apply(player);
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
		if (settings.silent()) {
			// Held back for the packet swap; the camera stays where the player put it.
			silentRotation = current;
			return;
		}

		silentRotation = null;
		player.setYaw(current.yaw());
		player.setPitch(current.pitch());
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
