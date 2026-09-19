package com.naruka.client.rotation;

/**
 * A pair of look angles.
 *
 * @param yaw   horizontal angle in degrees, wrapping at ±180
 * @param pitch vertical angle in degrees, clamped to ±90
 */
public record Rotation(float yaw, float pitch) {

	public static final float MAX_PITCH = 90.0f;

	/** @return a copy of this rotation with the pitch inside the legal range */
	public Rotation clamped() {
		return new Rotation(this.yaw, Math.clamp(this.pitch, -MAX_PITCH, MAX_PITCH));
	}

	/** @return the straight-line distance to another rotation in degrees, for logging and comparisons */
	public float distanceTo(Rotation other) {
		float yawDelta = net.minecraft.util.math.MathHelper.wrapDegrees(other.yaw - this.yaw);
		return (float) Math.hypot(yawDelta, other.pitch - this.pitch);
	}
}
