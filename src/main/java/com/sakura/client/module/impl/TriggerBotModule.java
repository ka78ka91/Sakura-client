package com.sakura.client.module.impl;

import com.sakura.client.mixin.MinecraftClientAccessor;
import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import com.sakura.client.rotation.RotationManager;
import com.sakura.client.rotation.RotationMode;
import com.sakura.client.rotation.RotationSettings;
import com.sakura.client.safety.Clicker;
import com.sakura.client.safety.FlagDetector;
import com.sakura.client.setting.BooleanSetting;
import com.sakura.client.setting.ChanceSetting;
import com.sakura.client.setting.EnumSetting;
import com.sakura.client.setting.NumberSetting;
import com.sakura.client.setting.RangeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;

/**
 * Attacks whatever is under the crosshair.
 *
 * <p>Settings mirror LiquidBounce's {@code ModuleTriggerBot} (GPL-3.0): a range limit, a roll against a chance,
 * a rate limit, a weapon-charge floor and optional rotation. The rate limit reuses the same rolling
 * {@link Clicker} window as the auto-clicker.</p>
 *
 * <p>Rotation goes through {@link RotationManager}, so it can be silent: the hit is aimed server-side while the
 * camera stays where the player left it. {@code Pause On Flag} hands control back to the global
 * {@link FlagDetector}, which stops the module the moment the server corrects our position —the clearest sign
 * that our rotation claims are being checked.</p>
 */
public final class TriggerBotModule extends Module {

	private final NumberSetting range = setting(new NumberSetting("Range",
			"Only attack targets closer than this.", 3.0, 1.0, 6.0, 0.1, "blocks"));
	private final NumberSetting reactionDelay = setting(new NumberSetting("Reaction Delay",
			"Time between a target entering the crosshair and the first attack.", 150.0, 0.0, 500.0, 10.0,
			"ms"));
	private final RangeSetting cps = setting(new RangeSetting("CPS",
			"Attacks per second, rolled inside this range each time.", 8.0, 12.0, 1.0, 20.0, true, ""));
	private final NumberSetting cooldown = setting(new NumberSetting("Cooldown",
			"Hold the attack until the weapon is at least this charged. Vanilla scales attack damage by the "
					+ "attack cooldown, so attacking on a half-charged weapon hits for less than waiting.",
			0.9, 0.0, 1.0, 0.05, ""));
	private final ChanceSetting chance = setting(new ChanceSetting("Chance",
			"Chance of attacking a target that is in range.", 100.0));
	private final BooleanSetting rotate = setting(new BooleanSetting("Rotate",
			"Aim at the target before hitting it.", true));
	private final BooleanSetting pauseOnFlag = setting(new BooleanSetting("Pause On Flag",
			"Stop attacking while the server is correcting our position.", true));

	private final EnumSetting<RotationMode> rotationMode = setting(new EnumSetting<>("Rotation",
			"How the aim travels to the target.", RotationMode.LINEAR));
	private final NumberSetting rotationSpeed = setting(new NumberSetting("Rotation Speed",
			"Degrees of travel per tick.", 10.0, 0.5, 180.0, 0.5, "\u00B0/tick"));
	private final BooleanSetting silent = setting(new BooleanSetting("Silent",
			"Keep the aim out of the camera and only send it to the server.", true));
	private final NumberSetting resetThreshold = setting(new NumberSetting("Reset Threshold",
			"How close to the target counts as arrived.", 2.0, 0.5, 180.0, 0.5, "\u00B0"));
	private final NumberSetting ticksUntilReset = setting(new NumberSetting("Ticks Until Reset",
			"How long to keep aiming after the target is lost.", 5.0, 1.0, 30.0, 1.0, "ticks"));
	private final RotationSettings rotations = new RotationSettings(this.rotationMode, this.rotationSpeed,
			this.silent, this.resetThreshold, this.ticksUntilReset);

	private final Clicker clicker = new Clicker();
	private LivingEntity target;
	private long acquiredAt;
	private long nextClickAt;

	public TriggerBotModule() {
		super("TriggerBot", Category.COMBAT, "Hits whatever you look at.");

		this.rotationMode.visibleWhen(this.rotate::get);
		this.rotationSpeed.visibleWhen(this.rotate::get);
		this.silent.visibleWhen(this.rotate::get);
		this.resetThreshold.visibleWhen(this.rotate::get);
		this.ticksUntilReset.visibleWhen(this.rotate::get);
	}

	@Override
	public String getHudSuffix() {
		if (!isEnabled() || this.target == null) {
			return null;
		}

		return this.clicker.getCps() + " cps \u2192 " + this.target.getName().getString();
	}

	/** @return the entity this module is currently attacking, or {@code null} */
	public LivingEntity getTarget() {
		return this.target;
	}

	/** @return the rolling window of attacks this module has made */
	public Clicker getClicker() {
		return this.clicker;
	}

	@Override
	public void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();

		if (client.player == null || client.world == null) {
			this.target = null;
			return;
		}

		if (this.pauseOnFlag.get() && FlagDetector.shouldPause()) {
			this.target = null;
			return;
		}

		long now = System.currentTimeMillis();
		LivingEntity found = findTarget(client);

		// A newly acquired target starts a fresh reaction window; without it the first attack lands on the
		// tick right after acquisition, faster than any player reacts.
		if (found != this.target) {
			this.target = found;
			this.acquiredAt = now;
		}

		if (found == null) {
			return;
		}

		if (this.rotate.get()) {
			RotationManager.request(client.player, yawTo(client.player, found), pitchTo(client.player, found),
					this.rotations);
		}

		if (now - this.acquiredAt < this.reactionDelay.intValue()) {
			return;
		}

		if (now < this.nextClickAt) {
			return;
		}

		if (!this.chance.roll()) {
			return;
		}

		// Vanilla scales attack damage by the weapon's charge, and swallows the attack outright below its own
		// minimum, so a hit fired on a recharging weapon costs a click and lands for less. The rate limit
		// above is a ceiling, not a promise that the weapon is ready.
		if (client.player.getAttackCooldownProgress(0.5f) < this.cooldown.get()) {
			return;
		}

		((MinecraftClientAccessor) client).sakura$doAttack();
		this.clicker.registerClick(now);
		this.clicker.prune(now);
		this.nextClickAt = now + 1000L / Math.max(1, this.cps.randomInt());
	}

	/** @return the living entity under the crosshair and inside the range, or {@code null} */
	private LivingEntity findTarget(MinecraftClient client) {
		if (!(client.crosshairTarget instanceof EntityHitResult hit)) {
			return null;
		}

		Entity entity = hit.getEntity();

		if (!(entity instanceof LivingEntity living) || entity == client.player || !living.isAlive()) {
			return null;
		}

		return client.player.distanceTo(entity) <= this.range.get() ? living : null;
	}

	/** @return the yaw that points the player's eyes at the target */
	private static float yawTo(PlayerEntity player, Entity target) {
		double dx = target.getX() - player.getX();
		double dz = target.getZ() - player.getZ();
		return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
	}

	/** @return the pitch that points the player's eyes at the middle of the target */
	private static float pitchTo(PlayerEntity player, Entity target) {
		Box box = target.getBoundingBox();
		double dx = target.getX() - player.getX();
		double dz = target.getZ() - player.getZ();
		double dy = (box.minY + box.maxY) / 2.0 - player.getEyeY();
		return (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
	}
}
