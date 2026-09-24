package com.sakura.client.module.impl;

import com.sakura.client.mixin.MinecraftClientAccessor;
import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import com.sakura.client.safety.Clicker;
import com.sakura.client.setting.BooleanSetting;
import com.sakura.client.setting.EnumSetting;
import com.sakura.client.setting.NumberSetting;
import com.sakura.client.setting.RangeSetting;
import com.sakura.client.setting.Tagged;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Clicks for the player at a configurable rate.
 *
 * <p>Rate handling follows LiquidBounce's {@code ModuleAutoClicker} (GPL-3.0): the configured CPS is a range and
 * every interval is rolled inside it, so the gaps between clicks are never constant. The rolling
 * {@link Clicker} window is the same one LiquidBounce uses to report the achieved rate.</p>
 *
 * <p>The clicks themselves go through vanilla's own handlers, so the attack cooldown and the attack-range
 * component still apply and the client cannot attack faster than the game allows. The Cooldown setting decides
 * how much of that charge is required before a click is spent; see {@link #attackCharged(PlayerEntity)}.</p>
 */
public final class AutoClickerModule extends Module {

	private final EnumSetting<Trigger> trigger = setting(new EnumSetting<>("Trigger",
			"When the clicker may click.", Trigger.HOLD));
	private final RangeSetting cps = setting(new RangeSetting("CPS",
			"Clicks per second, rolled inside this range each time.", 8.0, 12.0, 1.0, 20.0, true, ""));
	private final NumberSetting cooldown = setting(new NumberSetting("Cooldown",
			"Attacks are held back until the weapon is at least this charged. Vanilla damage is scaled by the "
					+ "attack cooldown, so clicking before it fills deals less damage, not more.", 0.9, 0.0, 1.0,
			0.05, ""));
	private final BooleanSetting leftClick = setting(new BooleanSetting("Left Click",
			"Click the attack button.", true));
	private final BooleanSetting rightClick = setting(new BooleanSetting("Right Click",
			"Click the use button as well.", false));

	private final Clicker clicker = new Clicker();
	private long nextClickAt;

	public AutoClickerModule() {
		super("AutoClicker", Category.COMBAT, "Clicks for you at a set rate.");
	}

	@Override
	public String getHudSuffix() {
		return isEnabled() ? this.clicker.getCps() + " cps" : null;
	}

	/** @return the rolling window of clicks this module has made, for the HUD and for tests */
	public Clicker getClicker() {
		return this.clicker;
	}

	@Override
	public void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();

		if (client.player == null || client.options == null) {
			this.clicker.reset();
			return;
		}

		boolean active = this.trigger.get() == Trigger.ALWAYS || client.options.attackKey.isPressed();

		if (!active) {
			this.clicker.reset();
			return;
		}

		this.clicker.prune();

		long now = System.currentTimeMillis();

		if (now < this.nextClickAt) {
			return;
		}

		boolean clicked = false;

		if (this.leftClick.get() && attackCharged(client.player)) {
			((MinecraftClientAccessor) client).sakura$doAttack();
			clicked = true;
		}

		if (this.rightClick.get()) {
			((MinecraftClientAccessor) client).sakura$doItemUse();
			clicked = true;
		}

		if (clicked) {
			this.clicker.registerClick(now);
			this.nextClickAt = now + delay();
		}
	}

	/**
	 * @return whether the weapon is charged enough to attack
	 *
	 * <p>Vanilla multiplies attack damage by {@code getAttackCooldownProgress}, so a click that lands before
	 * the bar is full deals partial damage — clicking faster than the cooldown is strictly worse than waiting.
	 * Vanilla also swallows the attack entirely, and {@code doAttack} reports that by returning false, so
	 * without this gate the clicker would spend its CPS window and its next-click delay on hits that never
	 * happened.</p>
	 *
	 * <p>Only the attack is gated. Using an item has its own cooldown system and must keep working at the
	 * configured rate, which is why the right-click branch below is left alone.</p>
	 */
	private boolean attackCharged(PlayerEntity player) {
		return player.getAttackCooldownProgress(0.5f) >= this.cooldown.get();
	}

	/**
	 * @return milliseconds until the next click
	 *
	 * <p>Interim humanisation until the SafetyManager lands its log-normal distribution: the interval rolled
	 * from the CPS range gets a &plusmn;20% multiplicative jitter plus a small chance of a pause about twice
	 * as long, so the gaps stop reading as a metronome. The result is clamped to the fastest interval the
	 * configured range allows, so the jitter can never push the rate past its own ceiling.</p>
	 */
	private long delay() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		long base = 1000L / Math.max(1, this.cps.randomInt());
		long fastest = 1000L / Math.max(1, (int) Math.round(this.cps.getUpper()));
		long jittered = (long) (base * random.nextDouble(0.8, 1.2));

		if (random.nextDouble() < 0.05) {
			jittered *= 2L;
		}

		return Math.max(fastest, jittered);
	}

	/** When the clicker is allowed to click. */
	public enum Trigger implements Tagged {

		/** Only while the attack key is held, so the player stays in control of when it runs. */
		HOLD("Hold"),

		/** Whenever the module is on. */
		ALWAYS("Always");

		private final String label;

		Trigger(String label) {
			this.label = label;
		}

		@Override
		public String getTag() {
			return this.label;
		}
	}
}
