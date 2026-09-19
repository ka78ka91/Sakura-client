package com.sakura.client.module.impl;

import com.sakura.client.mixin.MinecraftClientAccessor;
import com.sakura.client.mixin.PlayerEntityAccessor;
import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import com.sakura.client.setting.BooleanSetting;
import com.sakura.client.setting.EnumSetting;
import com.sakura.client.setting.NumberSetting;
import com.sakura.client.setting.RangeSetting;
import com.sakura.client.setting.Risk;
import com.sakura.client.setting.Tagged;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Makes attacks land as critical hits.
 *
 * <p>Port of LiquidBounce's {@code ModuleCriticals} (GPL-3.0) and its six modes. The critical-hit test itself is
 * not re-implemented: {@link PlayerEntityAccessor} calls vanilla's own {@code isCriticalHit}, so the module asks
 * the game whether an attack would crit rather than guessing.</p>
 *
 * <h2>Modes</h2>
 * <ul>
 *     <li><b>Jump</b> (default) 鈥?a real jump first, then the attack once the player is falling. A genuine crit
 *     by vanilla's rules, which is why it is the default.</li>
 *     <li><b>Packet</b> 鈥?sends extra movement packets nudging the Y position so the server believes the player is
 *     falling. Several sub-modes exist because each server validates the nudge differently.</li>
 *     <li><b>NoGround</b> 鈥?every movement packet claims the player is airborne.</li>
 *     <li><b>Blink</b> 鈥?holds back outgoing packets for a few hundred milliseconds at a time, so the server's
 *     view of the player lags behind the fall.</li>
 *     <li><b>Timer</b> 鈥?slows the client's own clock while a crit is pending, stretching the fall.</li>
 *     <li><b>None</b> 鈥?the module does nothing.</li>
 * </ul>
 *
 * <p>Everything except Jump and None manipulates what the server is told and is labelled accordingly in the UI.</p>
 */
public final class CriticalsModule extends Module {

	/** Vanilla only crits when the attack cooldown is essentially full. */
	private static final float ATTACK_READY = 0.9f;

	private static CriticalsModule instance;

	private final EnumSetting<Mode> mode = setting(new EnumSetting<>("Mode",
			"How the module tries to force a critical hit.", Mode.JUMP));
	private final EnumSetting<PacketMode> packetMode = setting(new EnumSetting<>("Packet Mode",
			"Which Y nudge to send; servers validate them differently.", PacketMode.NO_CHEAT_PLUS));
	private final NumberSetting jumpHeight = setting(new NumberSetting("Height",
			"Upward motion of the crit jump.", 0.42, 0.1, 0.42, 0.01, ""));
	private final NumberSetting jumpRange = setting(new NumberSetting("Range",
			"How close a target has to be before the module bothers.", 4.0, 1.0, 6.0, 0.5, "blocks"));
	private final BooleanSetting optimizeForCooldown = setting(new BooleanSetting("Optimize For Cooldown",
			"Do not start a jump the attack cooldown will not be ready for.", true));
	private final RangeSetting blinkDelay = setting(new RangeSetting("Blink Delay",
			"How long each blink lasts, rolled inside this range.", 300.0, 600.0, 0.0, 1000.0, true, "ms"));
	private final NumberSetting blinkRange = setting(new NumberSetting("Blink Range",
			"How close a target has to be for the module to blink.", 4.0, 0.0, 10.0, 0.5, "blocks"));
	private final NumberSetting timerSpeed = setting(new NumberSetting("Timer Speed",
			"Client clock speed while a crit is pending.", 0.8, 0.1, 1.0, 0.05, "x"));
	private final NumberSetting timerRange = setting(new NumberSetting("Timer Range",
			"How close a target has to be for the module to slow the clock.", 4.0, 0.0, 10.0, 0.5, "blocks"));
	private final BooleanSetting stopSprinting = setting(new BooleanSetting("Stop Sprinting",
			"Stop sprinting when a crit is wanted. Vanilla never crits while sprinting.", false));
	private final BooleanSetting visuals = setting(new BooleanSetting("Visuals",
			"Show critical hit particles.", false));
	private final BooleanSetting fakeVisuals = setting(new BooleanSetting("Fake",
			"Show the particles even when the hit did not crit.", false));

	private final Deque<Packet<?>> queued = new ArrayDeque<>();
	private boolean blinking;
	private boolean flushing;
	private long blinkStartedAt;
	private int blinkDuration;
	private boolean pendingAttack;
	private int pendingTicks;
	private int tickCounter;

	public CriticalsModule() {
		super("Criticals", Category.COMBAT, "Turns your hits into critical hits.");
		instance = this;

		this.packetMode.visibleWhen(() -> this.mode.get() == Mode.PACKET);
		this.jumpHeight.visibleWhen(() -> this.mode.get() == Mode.JUMP);
		this.jumpRange.visibleWhen(() -> this.mode.get() == Mode.JUMP);
		this.optimizeForCooldown.visibleWhen(() -> this.mode.get() == Mode.JUMP);
		this.blinkDelay.visibleWhen(() -> this.mode.get() == Mode.BLINK);
		this.blinkRange.visibleWhen(() -> this.mode.get() == Mode.BLINK);
		this.timerSpeed.visibleWhen(() -> this.mode.get() == Mode.TIMER);
		this.timerRange.visibleWhen(() -> this.mode.get() == Mode.TIMER);
	}

	/** @return the registered module, or {@code null} before registration */
	public static CriticalsModule getInstance() {
		return instance;
	}

	@Override
	public String getHudSuffix() {
		return isEnabled() ? this.mode.get().getTag() : null;
	}

	@Override
	public void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();

		if (!isEnabled() || client.player == null || client.world == null) {
			if (this.blinking || !this.queued.isEmpty()) {
				stopBlinking(client);
			}

			this.pendingAttack = false;
			this.tickCounter = 0;
			return;
		}

		this.tickCounter++;

		switch (this.mode.get()) {
			case JUMP -> tickJump(client);
			case BLINK -> tickBlink(client);
			case TIMER -> tickTimer(client);
			default -> {
			}
		}
	}

	@Override
	public void onDisable() {
		stopBlinking(MinecraftClient.getInstance());
		releaseTimer();
		this.pendingAttack = false;
	}

	// -----------------------------------------------------------------------------------------------------------
	// Attack hook, called from CriticalsAttackMixin before vanilla's click handler runs
	// -----------------------------------------------------------------------------------------------------------

	/** @return true when the click should be swallowed and re-issued later */
	public boolean beforeAttack() {
		MinecraftClient client = MinecraftClient.getInstance();

		if (!isEnabled() || client.player == null || client.world == null) {
			return false;
		}

		LivingEntity target = crosshairTarget(client);

		if (target == null) {
			return false;
		}

		if (this.visuals.get()) {
			showVisuals(client.player, target);
		}

		return switch (this.mode.get()) {
			case PACKET -> {
				sendPacketSpoof(client);
				yield false;
			}
			case JUMP -> requestCritJump(client, target);
			default -> false;
		};
	}

	private boolean requestCritJump(MinecraftClient client, LivingEntity target) {
		PlayerEntity player = client.player;

		if (wouldCrit(player, target)) {
			return false;
		}

		if (!allowsCriticalHit(player, true)) {
			return false;
		}

		if (player.isSprinting()) {
			if (!this.stopSprinting.get()) {
				// Vanilla refuses to crit a sprinting player, so there is nothing to wait for.
				return false;
			}

			player.setSprinting(false);
			return false;
		}

		if (!player.isOnGround()) {
			// In the air the only thing left to do is wait out the rest of the rise.
			return player.getVelocity().y > 0.0;
		}

		if (this.optimizeForCooldown.get() && player.getAttackCooldownProgress(0.5f) < ATTACK_READY) {
			return false;
		}

		jump(player);
		this.pendingAttack = true;
		this.pendingTicks = 0;
		return true;
	}

	/** The jump is a real one: upward motion plus the ground flag, exactly what a vanilla jump does. */
	private void jump(PlayerEntity player) {
		Vec3d velocity = player.getVelocity();
		player.setVelocity(velocity.x, this.jumpHeight.get(), velocity.z);
		player.setOnGround(false);
	}

	private void tickJump(MinecraftClient client) {
		if (!this.pendingAttack) {
			return;
		}

		PlayerEntity player = client.player;
		LivingEntity nearby = nearestTarget(client, this.jumpRange.get());

		if (nearby == null) {
			this.pendingAttack = false;
			return;
		}

		if (!allowsCriticalHit(player, true)) {
			this.pendingAttack = false;
			return;
		}

		// The jump wants a target under the crosshair, not just one nearby: re-issuing the click while the
		// crosshair has drifted off the target would only waste the crit window on empty air.
		LivingEntity target = crosshairTarget(client);

		if (target == null) {
			if (++this.pendingTicks > 40) {
				this.pendingAttack = false;
			}

			return;
		}

		if (wouldCrit(player, target)) {
			if (client.attackCooldown > 0) {
				// The click would be swallowed by vanilla's cooldown; keep the crit window open instead.
				return;
			}

			this.pendingAttack = false;
			this.pendingTicks = 0;
			((MinecraftClientAccessor) client).sakura$doAttack();
			return;
		}

		if (++this.pendingTicks > 40) {
			// No crit in sight; hand control back rather than holding the click forever.
			this.pendingAttack = false;
		}
	}

	// -----------------------------------------------------------------------------------------------------------
	// Packet mode
	// -----------------------------------------------------------------------------------------------------------

	private void sendPacketSpoof(MinecraftClient client) {
		PlayerEntity player = client.player;

		if (!canDoCriticalHit(player, false)) {
			return;
		}

		switch (this.packetMode.get()) {
			case VANILLA -> {
				sendMoveSpoof(client, 0.2, false);
				sendMoveSpoof(client, 0.01, false);
			}
			case NO_CHEAT_PLUS -> {
				sendMoveSpoof(client, 0.11, false);
				sendMoveSpoof(client, 0.1100013579, false);
				sendMoveSpoof(client, 0.0000013579, false);
			}
			case FALLING -> {
				sendMoveSpoof(client, 0.0625, false);
				sendMoveSpoof(client, 0.0625013579, false);
				sendMoveSpoof(client, 0.0000013579, false);
			}
			case LOW -> {
				sendMoveSpoof(client, 1.0E-9, false);
				sendMoveSpoof(client, 0.0, false);
			}
			case DOWN -> sendMoveSpoof(client, -1.0E-9, false);
			case GRIM -> {
				// Only useful in the air, where the nudge is too small for movement simulation to notice.
				if (!player.isOnGround()) {
					sendMoveSpoof(client, -0.000001, false);
				}
			}
			case BLOCKS_MC -> {
				if (this.tickCounter % 4 == 0) {
					sendMoveSpoof(client, 0.0011, true);
					sendMoveSpoof(client, 0.0, false);
				}
			}
		}
	}

	/** Sends one full movement packet at the player's position, nudged by {@code yOffset}. */
	private void sendMoveSpoof(MinecraftClient client, double yOffset, boolean onGround) {
		if (client.getNetworkHandler() == null) {
			return;
		}

		PlayerEntity player = client.player;
		client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(player.getX(), player.getY() + yOffset,
				player.getZ(), player.getYaw(), player.getPitch(), onGround, player.horizontalCollision));
	}

	// -----------------------------------------------------------------------------------------------------------
	// Blink mode
	// -----------------------------------------------------------------------------------------------------------

	private void tickBlink(MinecraftClient client) {
		LivingEntity target = nearestTarget(client, this.blinkRange.get());

		if (target == null) {
			stopBlinking(client);
			return;
		}

		long now = System.currentTimeMillis();

		if (!this.blinking) {
			this.blinking = true;
			this.blinkStartedAt = now;
			this.blinkDuration = Math.max(1, this.blinkDelay.randomInt());
			return;
		}

		if (now - this.blinkStartedAt >= this.blinkDuration) {
			// Window over: everything held back goes out at once and a new window starts next tick.
			stopBlinking(client);
		}
	}

	private void stopBlinking(MinecraftClient client) {
		this.blinking = false;
		flush(client);
	}

	private void flush(MinecraftClient client) {
		if (this.queued.isEmpty() || client.getNetworkHandler() == null) {
			this.queued.clear();
			return;
		}

		this.flushing = true;

		try {
			while (!this.queued.isEmpty()) {
				client.getNetworkHandler().getConnection().send(this.queued.pollFirst());
			}
		} finally {
			this.flushing = false;
		}
	}

	/** Called from the connection mixin for every outgoing packet. */
	public static boolean shouldQueuePacket(Packet<?> packet) {
		CriticalsModule module = instance;

		if (module == null || !module.blinking || module.flushing || !module.isEnabled()) {
			return false;
		}

		if (module.mode.get() != Mode.BLINK) {
			return false;
		}

		if (isInteractionPacket(packet)) {
			return false;
		}

		module.queued.addLast(packet);
		return true;
	}

	/**
	 * Packets that must never be held back: everything that acts on the world or answers the server. LiquidBounce
	 * passes the same set through its blink for the same reason.
	 */
	private static boolean isInteractionPacket(Packet<?> packet) {
		return packet instanceof PlayerInteractEntityC2SPacket
				|| packet instanceof PlayerActionC2SPacket
				|| packet instanceof HandSwingC2SPacket;
	}

	// -----------------------------------------------------------------------------------------------------------
	// Timer mode
	// -----------------------------------------------------------------------------------------------------------

	private void tickTimer(MinecraftClient client) {
		LivingEntity target = nearestTarget(client, this.timerRange.get());

		if (target == null || wouldCrit(client.player, target) || !allowsCriticalHit(client.player, true)) {
			releaseTimer();
			return;
		}

		requestTimer(this.timerSpeed.get().floatValue());
	}

	private static float requestedTimerSpeed = 1.0f;
	private static long lastRealTime = -1L;
	private static long lastScaledTime = -1L;

	private static void requestTimer(float speed) {
		requestedTimerSpeed = speed;
	}

	private static void releaseTimer() {
		requestedTimerSpeed = 1.0f;
		lastRealTime = -1L;
		lastScaledTime = -1L;
	}

	/**
	 * Called from the render tick counter mixin with the game's own clock. Only deltas are scaled, so the clock
	 * never mixes time sources and never jumps backwards when the speed changes.
	 */
	public static long scaleTimerTime(long timeMillis) {
		if (requestedTimerSpeed == 1.0f) {
			return timeMillis;
		}

		if (lastRealTime < 0L) {
			lastRealTime = timeMillis;
			lastScaledTime = timeMillis;
			return timeMillis;
		}

		lastScaledTime += (long) ((timeMillis - lastRealTime) * requestedTimerSpeed);
		lastRealTime = timeMillis;
		return lastScaledTime;
	}

	// -----------------------------------------------------------------------------------------------------------
	// Shared conditions
	// -----------------------------------------------------------------------------------------------------------

	/** Called from the movement packet mixin. */
	public static boolean isNoGroundActive() {
		CriticalsModule module = instance;
		return module != null && module.isEnabled() && module.mode.get() == Mode.NO_GROUND;
	}

	private boolean wouldCrit(PlayerEntity player, Entity target) {
		return ((PlayerEntityAccessor) player).sakura$isCriticalHit(target);
	}

	/** LiquidBounce's condition list: everything that makes a critical hit impossible no matter what we do. */
	private static boolean allowsCriticalHit(PlayerEntity player, boolean ignoreOnGround) {
		if (player.isTouchingWater() || player.hasVehicle() || player.isClimbing() || player.hasNoGravity()) {
			return false;
		}

		if (player.getAbilities().flying || player.isUsingItem()) {
			return false;
		}

		if (player.hasStatusEffect(StatusEffects.LEVITATION) || player.hasStatusEffect(StatusEffects.BLINDNESS)
				|| player.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
			return false;
		}

		return ignoreOnGround || !player.isOnGround();
	}

	/** LiquidBounce's {@code canDoCriticalHit}: the conditions plus a nearly full attack cooldown. */
	private static boolean canDoCriticalHit(PlayerEntity player, boolean ignoreSprint) {
		if (!allowsCriticalHit(player, true)) {
			return false;
		}

		if (player.getAttackCooldownProgress(0.5f) < ATTACK_READY) {
			return false;
		}

		return ignoreSprint || !player.isSprinting();
	}

	private void showVisuals(PlayerEntity player, LivingEntity target) {
		if (!this.fakeVisuals.get() && !wouldCrit(player, target)) {
			return;
		}

		player.addCritParticles(target);
	}

	private static LivingEntity crosshairTarget(MinecraftClient client) {
		if (!(client.crosshairTarget instanceof EntityHitResult hit)) {
			return null;
		}

		Entity entity = hit.getEntity();

		if (!(entity instanceof LivingEntity living) || entity == client.player || !living.isAlive()) {
			return null;
		}

		return living;
	}

	private static LivingEntity nearestTarget(MinecraftClient client, double range) {
		if (client.player == null || client.world == null) {
			return null;
		}

		LivingEntity closest = null;
		double best = range * range;

		for (Entity entity : client.world.getEntities()) {
			if (!(entity instanceof LivingEntity living) || entity == client.player || !living.isAlive()) {
				continue;
			}

			double distance = client.player.squaredDistanceTo(entity);

			if (distance <= best) {
				best = distance;
				closest = living;
			}
		}

		return closest;
	}

	/** How the module forces a critical hit. */
	public enum Mode implements Tagged {

		/** Does nothing. */
		NONE("None", Risk.SAFE),

		/** Spoofs the position the server is told about. */
		PACKET("Packet", Risk.RISKY),

		/** Claims to be airborne in every movement packet. */
		NO_GROUND("NoGround", Risk.RISKY),

		/** Jumps for real and attacks on the way down. The only mode a vanilla server cannot tell apart. */
		JUMP("Jump", Risk.SAFE),

		/** Holds movement packets back so the server's view lags behind the fall. */
		BLINK("Blink", Risk.RISKY),

		/** Slows the client clock. Heavily profiled by anti-cheats. */
		TIMER("Timer", Risk.OUTDATED);

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

	/** Which Y nudge Packet mode sends. Ported from LiquidBounce's sub-mode list. */
	public enum PacketMode implements Tagged {

		VANILLA("Vanilla"),
		NO_CHEAT_PLUS("NoCheatPlus"),
		FALLING("Falling"),
		LOW("Low"),
		DOWN("Down"),
		GRIM("Grim"),
		BLOCKS_MC("BlocksMC");

		private final String label;

		PacketMode(String label) {
			this.label = label;
		}

		@Override
		public String getTag() {
			return this.label;
		}

		@Override
		public Risk getRisk() {
			return this == VANILLA ? Risk.RISKY : Risk.OUTDATED;
		}
	}
}
