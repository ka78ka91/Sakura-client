package com.sakura.client.module.impl;

import com.sakura.client.mixin.MinecraftClientAccessor;
import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import com.sakura.client.rotation.RotationManager;
import com.sakura.client.rotation.RotationMode;
import com.sakura.client.rotation.RotationSettings;
import com.sakura.client.safety.Clicker;
import com.sakura.client.safety.FlagDetector;
import com.sakura.client.safety.SafetyManager;
import com.sakura.client.setting.BooleanSetting;
import com.sakura.client.setting.ChanceSetting;
import com.sakura.client.setting.EnumSetting;
import com.sakura.client.setting.MultiChoiceSetting;
import com.sakura.client.setting.NumberSetting;
import com.sakura.client.setting.RangeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Attacks whatever comes into range, without the player having to aim or click.
 *
 * <p>Structured as the same pipeline LiquidBounce uses (GPL-3.0), because the parts have genuinely different
 * jobs and the split is what makes the module tunable: {@link TargetTracker} decides <em>which</em> entity,
 * {@link PointTracker} decides <em>where</em> on it, {@link RotationManager} does the turning, and this class
 * owns the decision to actually swing. The swing itself goes through vanilla's
 * {@code MinecraftClient.doAttack}, the same entry point the player's own mouse button uses, so the attack
 * range component, the attack charge gate and the swing animation all still apply and KeepSprint sees an
 * ordinary attack.</p>
 *
 * <h2>What is deliberately not here</h2>
 *
 * <ul>
 *     <li>No through-walls mode. Vanilla's {@code crosshairTarget} is not consulted at all, so the module can
 *     only ever attack what its own range check accepts; a "through walls" switch would be an attack on a
 *     different rule set rather than on this one.</li>
 *     <li>No multi-target. One target per tick, chosen by the configured priority.</li>
 *     <li>No auto-block. Nothing in this client uses an offhand shield action yet, and a half-ported one would
 *     be a risk with no visible benefit.</li>
 * </ul>
 *
 * <h2>Safety</h2>
 *
 * <p>Every layer the client already has applies unchanged: the interval between swings comes from the
 * log-normal distribution in {@link Clicker}, the per-second action budget from {@link SafetyManager}, and
 * {@code Pause On Flag} hands control back to {@link FlagDetector} the moment the server corrects our
 * position. The rotation is deliberately never instant — {@code Reaction Delay} and the jitter inside
 * {@link RotationManager} exist so the aim arrives the way a hand would.</p>
 */
public final class KillAuraModule extends Module implements TargetProvider {

	/** The live instance, so the HUD check and the target provider can reach it without a registry lookup. */
	private static KillAuraModule instance;

	private final NumberSetting range = setting(new NumberSetting("Range",
			"Furthest distance an entity is attacked at.", 3.0, 1.0, 6.0, 0.1, "m"));
	private final NumberSetting reactionDelay = setting(new NumberSetting("Reaction Delay",
			"Time between a target appearing and the first swing.", 150.0, 0.0, 500.0, 10.0, "ms"));
	private final RangeSetting cps = setting(new RangeSetting("CPS",
			"Attacks per second, rolled inside this range each time.", 8.0, 12.0, 1.0, 20.0, true, ""));
	private final NumberSetting cooldown = setting(new NumberSetting("Cooldown",
			"Hold the attack until the weapon is at least this charged, since vanilla scales damage by it.",
			0.9, 0.0, 1.0, 0.05, ""));
	private final ChanceSetting failRate = setting(new ChanceSetting("Fail Rate",
			"Chance that a swing is deliberately aimed to miss, so the aim is not perfect every time.", 15.0));

	private final EnumSetting<TargetTracker.Priority> priority = setting(new EnumSetting<>("Priority",
			"Which target wins when several are in range.", TargetTracker.Priority.DISTANCE));
	private final MultiChoiceSetting<EntityOverlayModule.EntityGroup> targets =
			setting(new MultiChoiceSetting<>("Targets",
					"Which kinds of entity are attacked.",
					List.of(EntityOverlayModule.EntityGroup.PLAYERS, EntityOverlayModule.EntityGroup.MOBS),
					EntityOverlayModule.EntityGroup.class));
	private final BooleanSetting ignoreInvisible = setting(new BooleanSetting("Ignore Invisible",
			"Skip entities that are invisible to the client.", true));

	private final EnumSetting<RotationMode> rotationMode = setting(new EnumSetting<>("Rotation",
			"How the aim travels to the target.", RotationMode.INTERPOLATION));
	private final NumberSetting rotationSpeed = setting(new NumberSetting("Rotation Speed",
			"Degrees of travel per tick.", 10.0, 0.5, 180.0, 0.5, "\u00B0/tick"));
	private final BooleanSetting silent = setting(new BooleanSetting("Silent",
			"Keep the aim out of the camera and only send it to the server.", true));
	private final NumberSetting resetThreshold = setting(new NumberSetting("Reset Threshold",
			"How close to the target counts as arrived.", 2.0, 0.5, 180.0, 0.5, "\u00B0"));
	private final NumberSetting ticksUntilReset = setting(new NumberSetting("Ticks Until Reset",
			"How long to keep aiming after the target is lost.", 5.0, 1.0, 30.0, 1.0, "ticks"));

	private final BooleanSetting pauseOnFlag = setting(new BooleanSetting("Pause On Flag",
			"Stop attacking while the server is correcting our position.", true));

	private final RotationSettings rotations = new RotationSettings(this.rotationMode, this.rotationSpeed,
			this.silent, this.resetThreshold, this.ticksUntilReset);
	private final TargetTracker tracker = new TargetTracker();
	private final Clicker clicker = new Clicker();

	private LivingEntity target;
	private long acquiredAt;
	private long nextClickAt;

	public KillAuraModule() {
		super("KillAura", Category.COMBAT, "Attacks entities that come into range.");

		instance = this;
		TargetProviders.register(this);
	}

	@Override
	public String getHudSuffix() {
		if (!isEnabled() || this.target == null) {
			return null;
		}

		return this.clicker.getCps() + " cps \u2192 " + this.target.getName().getString();
	}

	/** @return true while the aura is enabled and has something to attack, for the HUD and for tests */
	public static boolean isAiming() {
		KillAuraModule module = instance;
		return module != null && module.isEnabled() && module.target != null;
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
		this.clicker.reset();
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

		if (this.pauseOnFlag.get() && FlagDetector.shouldPause()) {
			this.target = null;
			return;
		}

		long now = System.currentTimeMillis();
		LivingEntity found = this.tracker.select(client, this.targets.get(), this.range.get(),
				this.ignoreInvisible.get(), this.priority.get());

		// A newly acquired target starts a fresh reaction window; without it the first swing would land on the
		// tick right after acquisition, faster than any hand reacts.
		if (found != this.target) {
			this.target = found;
			this.acquiredAt = now;
		}

		if (found == null) {
			return;
		}

		aimAt(client.player, found);
		swing(client, found, now);
	}

	/** Sends the aim for this tick, letting the configured reaction delay override the manager's derived one. */
	private void aimAt(ClientPlayerEntity player, LivingEntity entity) {
		Vec3d point = PointTracker.pointFor(entity, PointTracker.AimPoint.CENTER);

		RotationManager.setReactionOverride(this.reactionDelay.intValue());
		RotationManager.request(player, yawTo(player, point), pitchTo(player, point), this.rotations);
	}

	/** Swings if the target is old enough, the weapon is charged, the rate allows it and the budget is not spent. */
	private void swing(MinecraftClient client, LivingEntity entity, long now) {
		if (now - this.acquiredAt < this.reactionDelay.intValue()) {
			return;
		}

		if (now < this.nextClickAt) {
			return;
		}

		if (client.player.getAttackCooldownProgress(0.5f) < this.cooldown.get()) {
			// Vanilla scales damage by the weapon's charge and swallows a swing below its own minimum, so an
			// attack fired here would cost a click and land for less.
			return;
		}

		if (!SafetyManager.canAct(getName())) {
			return;
		}

		// A failed roll moves where the swing is aimed, not whether it happens: skipping would reduce how often
		// the module acts, which is a different signature from aiming badly.
		if (!this.failRate.roll()) {
			Vec3d miss = PointTracker.missAround(entity);
			RotationManager.request(client.player, yawTo(client.player, miss), pitchTo(client.player, miss),
					this.rotations);
		}

		((MinecraftClientAccessor) client).sakura$doAttack();
		SafetyManager.recordAction(getName());
		this.clicker.registerClick(now);
		this.clicker.prune(now);
		this.nextClickAt = now + Clicker.nextIntervalMillis(this.cps.getLower(), this.cps.getUpper());
	}

	// --------------------------------------------------------------------- maths

	/** @return the yaw that points the player's eyes at {@code point} */
	private static float yawTo(ClientPlayerEntity player, Vec3d point) {
		double dx = point.x - player.getX();
		double dz = point.z - player.getZ();
		return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
	}

	/** @return the pitch that points the player's eyes at {@code point} */
	private static float pitchTo(ClientPlayerEntity player, Vec3d point) {
		double dx = point.x - player.getX();
		double dz = point.z - player.getZ();
		double dy = point.y - player.getEyeY();
		return (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
	}
}
