package com.sakura.client.module.impl;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Chooses the point on a target the aim is sent to.
 *
 * <p>Kept apart from the aiming itself because the two answer different questions: which entity to look at is
 * target selection, and where on that entity to look is this. LiquidBounce splits them the same way, which is
 * what lets a single aim routine serve "middle of the box" and the per-target points that come later.</p>
 *
 * <p>Only the centre is implemented. {@code AimPoint} exists so the ranking and the aiming code already take a
 * point rather than an entity, which is the shape the extra points need; adding one is then a case in
 * {@link #pointFor} rather than a change to every caller.</p>
 */
final class PointTracker {

	/**
	 * How far a deliberate miss throws the aim, as a fraction of the target's own width.
	 *
	 * <p>Scaled to the target rather than to a fixed number of blocks so the miss is proportionally the same on
	 * a player and on a large mob: a fixed offset would always miss a small target and never miss a big one.</p>
	 */
	private static final double MISS_SPREAD = 0.65;
	/** A miss is thrown this much on each axis at most, so the shot lands near the edge instead of past it. */
	private static final double MISS_VERTICAL_SPREAD = 0.35;

	private PointTracker() {
	}

	/** Where on a target an aim can be sent. */
	enum AimPoint {
		/** Middle of the hitbox: the default, and the only one implemented so far. */
		CENTER
	}

	/**
	 * @return the point to aim at, exactly on the target
	 */
	static Vec3d pointFor(LivingEntity entity, AimPoint point) {
		return switch (point) {
			case CENTER -> TargetTracker.centreOf(entity);
		};
	}

	/**
	 * @return a point near the centre that is meant to be missed
	 *
	 * <p>A deliberate miss is thrown sideways and vertically by up to the target's own size rather than skipped
	 * outright. Skipping would reduce how often the module acts, which is a different signature from aiming
	 * badly; a miss keeps the click cadence a person would have and only moves where the shot goes, which is what
	 * a real player's sprays look like.</p>
	 */
	static Vec3d missAround(LivingEntity entity) {
		Vec3d centre = TargetTracker.centreOf(entity);
		double width = Math.max(0.4, entity.getBoundingBox().getLengthX());
		double height = Math.max(0.4, entity.getBoundingBox().getLengthY());
		// A random direction in the horizontal plane, so misses are spread around the target rather than always
		// thrown along the same diagonal.
		double angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
		double reach = ThreadLocalRandom.current().nextDouble(0.0, width * MISS_SPREAD);
		double pitchOffset = ThreadLocalRandom.current().nextDouble(-1.0, 1.0) * height * MISS_VERTICAL_SPREAD;

		return new Vec3d(centre.x + Math.cos(angle) * reach, centre.y + pitchOffset,
				centre.z + Math.sin(angle) * reach);
	}
}
