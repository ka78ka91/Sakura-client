package com.sakura.client.module.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import com.sakura.client.setting.Tagged;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Picks the entity a combat module should act on.
 *
 * <p>The candidate scan and the group classification are the ones the world overlays already use: the same
 * {@code EntityGroup} setting, the same "never yourself, never what the camera is riding, never a dead or
 * removed entity, optionally never an invisible one, never past the range" rules, and the same
 * {@link EntityGroup#groupOf} implementation. Keeping one classification is what makes a module set to
 * {@code Players} mean the same thing in ESP, in NameTags and in an aura; only the ranking differs, because an
 * overlay draws everything it found while an aura has to choose one.</p>
 *
 * <p>The candidate list is reused between calls and only ever touched from the tick thread, so picking a target
 * allocates nothing per tick beyond the candidate it finally returns.</p>
 */
final class TargetTracker {

	/** Reused between ticks; cleared at the start of every scan. */
	private final List<LivingEntity> candidates = new ArrayList<>();

	/** Modules own one tracker each, so the scratch list is never shared across modules. */
	TargetTracker() {
	}

	/**
	 * @param groups    which kinds of entity are eligible
	 * @param range     furthest distance, in blocks
	 * @param invisible whether entities invisible to the client are skipped
	 * @param priority  how the survivors are ranked
	 * @return the best target, or {@code null} when nothing qualifies
	 */
	LivingEntity select(MinecraftClient client, Set<EntityOverlayModule.EntityGroup> groups, double range,
						boolean invisible, Priority priority) {
		this.candidates.clear();

		ClientPlayerEntity player = client.player;

		if (player == null || client.world == null || groups.isEmpty()) {
			return null;
		}

		Entity camera = client.getCameraEntity();
		double limitSquared = range * range;

		for (Entity entity : client.world.getEntities()) {
			if (entity == player || entity == camera || entity.isRemoved() || !entity.isAlive()) {
				continue;
			}

			if (!(entity instanceof LivingEntity living)) {
				continue;
			}

			if (invisible && entity.isInvisible()) {
				continue;
			}

			if (entity.squaredDistanceTo(player) > limitSquared) {
				continue;
			}

			if (!groups.contains(EntityOverlayModule.groupOf(entity))) {
				continue;
			}

			this.candidates.add(living);
		}

		if (this.candidates.isEmpty()) {
			return null;
		}

		Comparator<LivingEntity> ranking = switch (priority) {
			case DISTANCE -> Comparator.comparingDouble(entity -> entity.squaredDistanceTo(player));
			case HEALTH -> Comparator.comparingDouble(LivingEntity::getHealth);
			case ANGLE -> Comparator.comparingDouble(entity -> angleTo(player, entity));
		};

		this.candidates.sort(ranking);
		return this.candidates.get(0);
	}

	/**
	 * @return how far the player's current view is from the entity's centre, in degrees
	 *
	 * <p>Computed from the same yaw and pitch the aim would use, so "closest to the crosshair" means the least
	 * turning rather than a raw 3D distance.</p>
	 */
	private static double angleTo(ClientPlayerEntity player, LivingEntity entity) {
		Vec3d eye = player.getEyePos();
		Vec3d centre = centreOf(entity);
		double dx = centre.x - eye.x;
		double dy = centre.y - eye.y;
		double dz = centre.z - eye.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);

		float wantedYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float wantedPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

		float yawGap = Math.abs(MathHelper.wrapDegrees(wantedYaw - player.getYaw()));
		float pitchGap = Math.abs(wantedPitch - player.getPitch());
		return Math.sqrt(yawGap * yawGap + pitchGap * pitchGap);
	}

	/** @return the middle of an entity's hitbox, which is what an aim point is derived from */
	static Vec3d centreOf(LivingEntity entity) {
		return new Vec3d(entity.getX(), entity.getBoundingBox().getCenter().y, entity.getZ());
	}

	/** How the survivors of the scan are ranked. */
	enum Priority implements Tagged {
		/** Nearest first: the default, and the one that reaches a threat fastest. */
		DISTANCE("Distance"),

		/** Weakest first: finishes a target instead of spreading damage. */
		HEALTH("Health"),

		/** Least turning first: the smallest rotation, so the aim is hardest to notice. */
		ANGLE("Angle");

		private final String label;

		Priority(String label) {
			this.label = label;
		}

		@Override
		public String getTag() {
			return this.label;
		}
	}
}
