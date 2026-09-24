package com.sakura.client.module.impl;

import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import com.sakura.client.rotation.RotationManager;
import com.sakura.client.rotation.RotationMode;
import com.sakura.client.rotation.RotationSettings;
import com.sakura.client.setting.BooleanSetting;
import com.sakura.client.setting.EnumSetting;
import com.sakura.client.setting.MultiChoiceSetting;
import com.sakura.client.setting.NumberSetting;
import com.sakura.client.setting.Tagged;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Turns the camera toward whatever is near the crosshair. It never attacks.
 *
 * <p>The difference from {@link KillAuraModule} is the whole point: an aura decides both where to look and when
 * to swing, while this only helps with the first half and leaves the click to the player. That makes it a
 * strictly weaker thing to have switched on — every attack still comes from a real button press — and it is why
 * the two are separate modules rather than one module with a mode.</p>
 *
 * <p>It shares the pipeline rather than reimplementing it: the same {@link TargetTracker} picks the entity, the
 * same {@code RotationManager} does the turning with the same jitter and reaction delay, and the same
 * {@link TargetProvider} registry lets the TargetHUD show what it is aiming at. Only the axes and the
 * mouse-blend behaviour are its own.</p>
 *
 * <h2>Mouse blend</h2>
 *
 * <p>Aiming assistance that keeps pulling while the player is already moving the mouse feels like fighting the
 * game, and it also means the client is steering at exactly the moment the player is — which is the opposite of
 * looking human. So the module watches the mouse through {@code MouseMixin} and stands down the moment the mouse
 * moves, then eases back in after a short delay. The delay is what {@code Blend Strength} controls, and the
 * names are chosen so the number reads the way it behaves: a high strength yields for longer, a low one takes
 * over again almost immediately, and zero means the module never yields and the mouse does not matter.</p>
 */
public final class AimbotModule extends Module implements TargetProvider {

	private static AimbotModule instance;

	/** Longest the aim stands down after the mouse moves, at maximum Blend Strength. */
	private static final long BLEND_MAX_MILLIS = 500L;
	/** The aim never stands down for less than this: a yield that lasts no ticks would not be a yield. */
	private static final long BLEND_MIN_MILLIS = 50L;

	private final NumberSetting range = setting(new NumberSetting("Range",
			"Furthest distance an entity is aimed at.", 3.0, 1.0, 6.0, 0.1, "m"));
	private final EnumSetting<RotationMode> rotationMode = setting(new EnumSetting<>("Rotation",
			"Shape of the turn toward the target.", RotationMode.SIGMOID));
	private final NumberSetting rotationSpeed = setting(new NumberSetting("Rotation Speed",
			"Degrees of travel per tick.", 10.0, 0.5, 180.0, 0.5, "\u00B0/tick"));
	private final EnumSetting<Axis> axis = setting(new EnumSetting<>("Axis",
			"Which axes the aim is allowed to move.", Axis.BOTH));
	private final BooleanSetting silent = setting(new BooleanSetting("Silent",
			"Keep the aim out of the camera and only send it to the server.", true));
	private final BooleanSetting mouseBlend = setting(new BooleanSetting("Mouse Blend",
			"Stand down while the player is moving the mouse themselves.", true));
	private final NumberSetting blendStrength = setting(new NumberSetting("Blend Strength",
			"How long the aim keeps yielding after the mouse moves. 0 never yields at all.", 60.0, 0.0, 100.0,
			5.0, "%"));

	private final EnumSetting<TargetTracker.Priority> priority = setting(new EnumSetting<>("Priority",
			"Which target wins when several are nearby.", TargetTracker.Priority.ANGLE));
	private final MultiChoiceSetting<EntityOverlayModule.EntityGroup> targets =
			setting(new MultiChoiceSetting<>("Targets",
					"Which kinds of entity are aimed at.",
					List.of(EntityOverlayModule.EntityGroup.PLAYERS, EntityOverlayModule.EntityGroup.MOBS),
					EntityOverlayModule.EntityGroup.class));
	private final BooleanSetting ignoreInvisible = setting(new BooleanSetting("Ignore Invisible",
			"Skip entities that are invisible to the client.", true));

	private final NumberSetting resetThreshold = setting(new NumberSetting("Reset Threshold",
			"How close to the target counts as arrived.", 2.0, 0.5, 180.0, 0.5, "\u00B0"));
	private final NumberSetting ticksUntilReset = setting(new NumberSetting("Ticks Until Reset",
			"How long to keep aiming after the target is lost.", 5.0, 1.0, 30.0, 1.0, "ticks"));

	private final RotationSettings rotations = new RotationSettings(this.rotationMode, this.rotationSpeed,
			this.silent, this.resetThreshold, this.ticksUntilReset);
	private final TargetTracker tracker = new TargetTracker();

	private LivingEntity target;

	/** Wall-clock moment until which the aim stands down, or {@code 0} while it is in control. */
	private static long manualUntil;

	public AimbotModule() {
		super("Aimbot", Category.COMBAT, "Turns the camera toward entities near the crosshair.");

		this.blendStrength.visibleWhen(this.mouseBlend::get);

		instance = this;
		TargetProviders.register(this);
	}

	/**
	 * Called from {@code MouseMixin} whenever the player moved the mouse during the last tick.
	 *
	 * <p>Static because the mixin has no way to reach the module instance, and it only ever needs to record a
	 * deadline. With Mouse Blend off there is nothing to record, so the call is dropped immediately.</p>
	 */
	public static void onManualMouseMove() {
		AimbotModule module = instance;

		if (module == null || !module.isEnabled() || !module.mouseBlend.get()) {
			return;
		}

		manualUntil = System.currentTimeMillis() + module.blendWindowMillis();
	}

	@Override
	public String getHudSuffix() {
		return isEnabled() && this.target != null ? this.target.getName().getString() : null;
	}

	// ------------------------------------------------------------------ provider

	@Override
	public LivingEntity getTarget() {
		return this.target;
	}

	@Override
	public boolean isProviding() {
		return isEnabled();
	}

	// -------------------------------------------------------------------- ticking

	@Override
	public void onDisable() {
		this.target = null;
		manualUntil = 0L;
		// Hand the aim back rather than leaving a silent rotation in the packet path.
		RotationManager.reset();
	}

	@Override
	public void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();

		if (!isEnabled() || client.player == null || client.world == null) {
			this.target = null;
			return;
		}

		this.target = this.tracker.select(client, this.targets.get(), this.range.get(),
				this.ignoreInvisible.get(), this.priority.get());

		if (this.target == null) {
			return;
		}

		// Standing down means the player's own mouse is in charge right now. Releasing the aim instead of just
		// skipping the request is deliberate: RotationManager would otherwise keep steering for
		// TicksUntilReset ticks after the last request, which is the fight this setting exists to avoid.
		if (isYielding()) {
			RotationManager.reset();
			return;
		}

		aimAt(client.player, this.target);
	}

	private void aimAt(ClientPlayerEntity player, LivingEntity entity) {
		Vec3d point = PointTracker.pointFor(entity, PointTracker.AimPoint.CENTER);
		float wantedYaw = yawTo(player, point);
		float wantedPitch = pitchTo(player, point);

		// An axis the module is not allowed to move is pinned to where the player left it, so "horizontal only"
		// still aims and still stops the view from drifting vertically.
		if (this.axis.get() == Axis.HORIZONTAL) {
			wantedPitch = player.getPitch();
		} else if (this.axis.get() == Axis.VERTICAL) {
			wantedYaw = player.getYaw();
		}

		RotationManager.request(player, wantedYaw, wantedPitch, this.rotations);
	}

	/** @return whether the player has moved the mouse recently enough that the aim should stay out of the way */
	private boolean isYielding() {
		return manualUntil != 0L && System.currentTimeMillis() < manualUntil;
	}

	/** @return how long to stand down after a mouse movement, from the configured strength */
	private long blendWindowMillis() {
		double strength = Math.clamp(this.blendStrength.get() / 100.0, 0.0, 1.0);

		if (strength <= 0.0) {
			return 0L;
		}

		return Math.round(BLEND_MIN_MILLIS + (BLEND_MAX_MILLIS - BLEND_MIN_MILLIS) * strength);
	}

	// --------------------------------------------------------------------- maths

	private static float yawTo(ClientPlayerEntity player, Vec3d point) {
		double dx = point.x - player.getX();
		double dz = point.z - player.getZ();
		return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
	}

	private static float pitchTo(ClientPlayerEntity player, Vec3d point) {
		double dx = point.x - player.getX();
		double dz = point.z - player.getZ();
		double dy = point.y - player.getEyeY();
		return (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
	}

	/** Which axes the aim may move. */
	enum Axis implements Tagged {

		HORIZONTAL("Horizontal"),
		VERTICAL("Vertical"),
		BOTH("Both");

		private final String label;

		Axis(String label) {
			this.label = label;
		}

		@Override
		public String getTag() {
			return this.label;
		}
	}
}
