package com.sakura.client.module.impl;

import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Stops the player at the edge of a block instead of letting them walk off it.
 *
 * <p>The speed the movement input is multiplied by while sneaking, which is what keeps a sneaking player from
 * stepping off a ledge. Vanilla applies it in the same place this module does — to the movement input, before
 * the game moves the entity — and it is a plain constant there rather than a public field, so it is written out
 * here with the note that a version change would be the thing to re-check.</p>
 */
public final class SafeWalkModule extends Module {

	/** Vanilla's movement multiplier while sneaking. */
	private static final float SNEAK_SPEED = 0.3f;

	/** How far ahead of the player the ground is checked, in blocks. */
	private static final double PROBE_DISTANCE = 0.35;

	/**
	 * How far below the feet the probe looks for ground.
	 *
	 * <p>Small on purpose: the question is only "is there a block directly under this point". Looking further
	 * down would report ground underneath a ledge the player is standing at the very edge of.</p>
	 */
	private static final double PROBE_DEPTH = 0.2;

	/** The registered module, so the mixin can reach it without a lookup. */
	private static SafeWalkModule instance;

	/** Whether the player is standing at an edge right now, computed once per tick. */
	private boolean active;

	public SafeWalkModule() {
		super("SafeWalk", Category.MOVEMENT, "Stops you at the edge of a block instead of walking off.");

		instance = this;
	}

	@Override
	public void onDisable() {
		this.active = false;
	}

	@Override
	public void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;

		if (!isEnabled() || player == null || client.world == null) {
			this.active = false;
			return;
		}

		// Only on the ground: in the air there is no edge to stop at, and the slowdown would only interfere
		// with steering a fall.
		this.active = player.isOnGround() && !player.isSneaking() && atEdge(player);
	}

	/**
	 * @return the movement input to use for this tick, scaled down when the player is at an edge
	 *
	 * <p>Called from the mixin in place of the raw input. Scaling rather than blocking is deliberate: blocking
	 * would stop the player dead every time they walked toward a ledge, while the sneaking multiplier is exactly
	 * what vanilla uses to let a sneaking player creep up to the lip and stop.</p>
	 */
	public static Vec2f maybeSlowAtEdge(Vec2f movement) {
		SafeWalkModule module = instance;

		if (module == null || !module.isEnabled() || !module.active) {
			return movement;
		}

		return movement.multiply(SNEAK_SPEED);
	}

	/** @return whether the ground is missing a little way ahead in the direction the player is moving */
	private boolean atEdge(ClientPlayerEntity player) {
		Vec2f movement = player.input == null ? null : player.input.getMovementInput();

		if (movement == null || (movement.x == 0.0f && movement.y == 0.0f)) {
			// Standing still: nothing is pushing the player toward the edge, so there is nothing to hold back.
			return false;
		}

		double yaw = Math.toRadians(player.getYaw());
		// Movement is in the player's own frame: +y is forward, +x is to the left.
		double dx = -Math.sin(yaw) * movement.y + Math.cos(yaw) * movement.x;
		double dz = Math.cos(yaw) * movement.y + Math.sin(yaw) * movement.x;
		double length = Math.hypot(dx, dz);

		if (length < 1.0E-4) {
			return false;
		}

		double probeX = player.getX() + dx / length * PROBE_DISTANCE;
		double probeZ = player.getZ() + dz / length * PROBE_DISTANCE;
		Vec3d start = new Vec3d(probeX, player.getY() + 0.05, probeZ);
		Vec3d end = start.add(0.0, -PROBE_DEPTH, 0.0);

		BlockHitResult hit = player.getEntityWorld().raycast(new RaycastContext(start, end,
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));

		return hit.getType() != HitResult.Type.BLOCK;
	}
}
