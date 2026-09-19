package com.naruka.client.module.impl;

import com.naruka.client.module.Category;
import com.naruka.client.module.Module;
import com.naruka.client.safety.FlagDetector;
import com.naruka.client.setting.BooleanSetting;
import com.naruka.client.setting.ChanceSetting;
import com.naruka.client.setting.EnumSetting;
import com.naruka.client.setting.NumberSetting;
import com.naruka.client.setting.RangeSetting;
import com.naruka.client.setting.Risk;
import com.naruka.client.setting.Tagged;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;

/**
 * Reduces the knockback taken from hits, ported from LiquidBounce's {@code ModuleVelocity} (GPL-3.0).
 *
 * <p>The packet hook and the delay/pause plumbing are in place, and the default mode is implemented and verified.
 * LiquidBounce ships thirteen further modes — six generic ones and eight written against a specific server or
 * anti-cheat. Their sources are on disk and they are the next thing to port; they are deliberately absent from the
 * mode list until then, because a mode that does not do what its name says is worse than no mode at all.</p>
 *
 * <h2>JumpReset</h2>
 *
 * <p>The technique the module is named for: jump on the first tick of the hurt animation, on the ground and while
 * sprinting. A jump resets the sprint state, and vanilla applies less knockback to a player who is not sprinting,
 * so the jump quietly eats part of the hit. Nothing is spoofed and nothing is cancelled, which is why this is the
 * default and the only mode here so far.</p>
 */
public final class VelocityModule extends Module {

	private static VelocityModule instance;

	/** Vanilla jump motion; the same value the game uses when the player presses jump. */
	private static final double JUMP_MOTION = 0.42;

	/** The first tick of the hurt animation, which is when the knockback is about to be applied. */
	private static final int HURT_TICK = 9;

	private final EnumSetting<Mode> mode = setting(new EnumSetting<>("Mode",
			"How the module deals with knockback.", Mode.JUMP_RESET));
	private final ChanceSetting chance = setting(new ChanceSetting("Chance",
			"Chance of reacting to a hit.", 100.0));
	private final BooleanSetting jumpByDelay = setting(new BooleanSetting("Jump By Delay",
			"Wait a random number of ticks after a hit before jumping.", true));
	private final RangeSetting ticksUntilJump = setting(new RangeSetting("Until Jump",
			"Ticks to wait after a hit, rolled inside this range.", 2.0, 2.0, 0.0, 20.0, true, "ticks"));
	private final BooleanSetting jumpByHits = setting(new BooleanSetting("Jump By Received Hits",
			"Wait a random number of hits before jumping.", false));
	private final RangeSetting hitsUntilJump = setting(new RangeSetting("Hits Until Jump",
			"Hits to wait before jumping, rolled inside this range.", 2.0, 2.0, 0.0, 10.0, true, "hits"));
	private final NumberSetting pauseOnFlag = setting(new NumberSetting("Pause On Flag",
			"Stop reacting for this many ticks after the server corrects our position.", 0.0, 0.0, 20.0, 1.0,
			"ticks"));

	private boolean knockbackReceived;
	private boolean fallDamage;
	private int limitUntilJump;
	private int limitTicks;
	private int targetHits;
	private int targetTicks;

	public VelocityModule() {
		super("Velocity", Category.COMBAT, "Takes less knockback.");

		this.ticksUntilJump.visibleWhen(this.jumpByDelay::get);
		this.hitsUntilJump.visibleWhen(this.jumpByHits::get);

		this.targetHits = 2;
		this.targetTicks = 2;
		instance = this;
	}

	/** Called from the velocity packet mixin, before the packet is applied. */
	public static void onVelocityPacket(EntityVelocityUpdateS2CPacket packet) {
		VelocityModule module = instance;

		if (module == null || !module.isEnabled() || MinecraftClient.getInstance().player == null) {
			return;
		}

		if (packet.getEntityId() != MinecraftClient.getInstance().player.getId()) {
			return;
		}

		Vec3d velocity = packet.getVelocity();
		// A knockback with no horizontal motion and downward Y is fall damage, not a hit.
		module.fallDamage = velocity.x == 0.0 && velocity.z == 0.0 && velocity.y < 0.0;
		module.knockbackReceived = true;
		module.limitTicks = 0;
	}

	@Override
	public void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();

		if (!isEnabled() || client.player == null) {
			reset();
			return;
		}

		this.limitTicks++;

		if (this.mode.get() != Mode.JUMP_RESET) {
			return;
		}

		if (this.pauseOnFlag.get() > 0.0 && FlagDetector.shouldPause()) {
			return;
		}

		PlayerEntity player = client.player;

		if (player.hurtTime != HURT_TICK || !player.isOnGround() || !player.isSprinting() || this.fallDamage
				|| !isCooldownOver() || !this.chance.roll()) {
			updateLimit(player);
			return;
		}

		jump(player);
		this.limitUntilJump = 0;
		this.targetHits = Math.max(0, this.hitsUntilJump.randomInt());
		this.targetTicks = Math.max(0, this.ticksUntilJump.randomInt());
	}

	private void jump(PlayerEntity player) {
		Vec3d velocity = player.getVelocity();
		player.setVelocity(velocity.x, JUMP_MOTION, velocity.z);
		player.setOnGround(false);
	}

	/**
	 * Whether the waiting period is over. LiquidBounce counts either hits or ticks, and reacts to every hit when
	 * neither counter is enabled; that third case is kept.
	 */
	private boolean isCooldownOver() {
		if (this.jumpByHits.get()) {
			return this.limitUntilJump >= this.targetHits;
		}

		if (this.jumpByDelay.get()) {
			return this.limitUntilJump >= this.targetTicks;
		}

		return true;
	}

	private void updateLimit(PlayerEntity player) {
		if (this.jumpByHits.get()) {
			if (player.hurtTime == HURT_TICK) {
				this.limitUntilJump++;
			}

			return;
		}

		this.limitUntilJump++;
	}

	private void reset() {
		this.limitUntilJump = 0;
		this.limitTicks = 0;
		this.knockbackReceived = false;
		this.fallDamage = false;
	}

	@Override
	public void onDisable() {
		reset();
	}

	/** @return true when a knockback packet has arrived since the last time this was asked */
	public boolean consumeKnockback() {
		boolean received = this.knockbackReceived;
		this.knockbackReceived = false;
		return received;
	}

	public int getLimitTicks() {
		return this.limitTicks;
	}

	/** How the module deals with knockback. Only the implemented modes are listed. */
	public enum Mode implements Tagged {

		/** Jump on the first hurt tick to cut the knockback a sprinting player would take. */
		JUMP_RESET("JumpReset", Risk.SAFE);

		private final String label;
		private final Risk risk;

		Mode(String label, Risk risk) {
			this.label = label;
			this.risk = risk;
		}

		@Override
		public String getTag() {
			return this.label;
		}

		@Override
		public Risk getRisk() {
			return this.risk;
		}
	}
}
