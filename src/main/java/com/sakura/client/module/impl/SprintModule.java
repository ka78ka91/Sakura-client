package com.sakura.client.module.impl;

import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import com.sakura.client.setting.BooleanSetting;
import com.sakura.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec2f;

/**
 * Keeps the player sprinting while they are moving forward.
 *
 * <p>Vanilla already sprints on a double-tap or while the sprint key is held, but it drops the sprint the moment
 * the player stops moving, eats something, or takes a hit. This holds it on instead, re-applying it every tick
 * as long as the conditions for sprinting still hold, which is the same thing as holding the key down.</p>
 *
 * <p>It is implemented entirely through {@code setSprinting}, which is the public state vanilla itself sets —
 * no packet is fabricated and no input is spoofed. The server can tell a sprinting player from a walking one
 * because vanilla sends that flag; all this changes is how often the client decides to be sprinting. Rated safe
 * for that reason.</p>
 *
 * <h2>Why the conditions are checked here</h2>
 *
 * <p>Sprinting is not free: it drains hunger, it changes knockback, and it is ignored entirely while the player
 * is using an item or has the Blindness effect. Re-applying it unconditionally would fight the game's own rules
 * and produce a state the server can disagree with, so every condition vanilla checks is checked here too.</p>
 */
public final class SprintModule extends Module {

	private final BooleanSetting onlyForward = setting(new BooleanSetting("Only Forward",
			"Keep sprinting only while moving forward, the way a held sprint key behaves.", true));
	private final NumberSetting minimumHunger = setting(new NumberSetting("Minimum Hunger",
			"Stop sprinting below this food level, since vanilla will not let you sprint hungry.", 6.0, 0.0, 20.0,
			1.0, ""));
	private final BooleanSetting stopWhileUsing = setting(new BooleanSetting("Stop While Using",
			"Stop sprinting while eating, drinking or blocking, which is what vanilla does.", true));

	public SprintModule() {
		super("Sprint", Category.MOVEMENT, "Keeps you sprinting while you move.");
	}

	@Override
	public void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;

		if (!isEnabled() || player == null || client.currentScreen != null) {
			return;
		}

		if (!canSprint(client, player)) {
			return;
		}

		player.setSprinting(true);
	}

	/** @return whether vanilla would accept a sprint request from this player right now */
	private boolean canSprint(MinecraftClient client, ClientPlayerEntity player) {
		if (player.isSneaking() || player.isGliding() || player.isSwimming() || player.isRiding()) {
			return false;
		}

		if (player.getHungerManager().getFoodLevel() <= this.minimumHunger.intValue()) {
			return false;
		}

		if (this.stopWhileUsing.get() && player.isUsingItem()) {
			return false;
		}

		if (player.hasStatusEffect(StatusEffects.BLINDNESS)) {
			return false;
		}

		// Moving forward at all is the condition a held sprint key uses; without it the module would also
		// sprint while strafing, which vanilla never does.
		return !this.onlyForward.get() || forwardInput(player);
	}

	/**
	 * @return whether the player is pushing forward
	 *
	 * <p>Read from the movement vector rather than from the key binding, so it also accounts for any other
	 * module that is driving the movement.</p>
	 */
	private static boolean forwardInput(ClientPlayerEntity player) {
		Vec2f movement = player.input == null ? null : player.input.getMovementInput();

		return movement != null && movement.y > 1.0E-5f;
	}
}
