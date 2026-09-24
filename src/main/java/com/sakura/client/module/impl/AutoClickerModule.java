package com.sakura.client.module.impl;

import com.sakura.client.mixin.MinecraftClientAccessor;
import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import com.sakura.client.safety.Clicker;
import com.sakura.client.setting.BooleanSetting;
import com.sakura.client.setting.EnumSetting;
import com.sakura.client.setting.RangeSetting;
import com.sakura.client.setting.Tagged;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Clicks for the player at a configurable rate.
 *
 * <p>Rate handling follows LiquidBounce's {@code ModuleAutoClicker} (GPL-3.0): the configured CPS is a range and
 * every interval is rolled inside it, so the gaps between clicks are never constant. The rolling
 * {@link Clicker} window is the same one LiquidBounce uses to report the achieved rate.</p>
 *
 * <p>The clicks themselves go through vanilla's own handlers, so the attack cooldown and the attack-range
 * component still apply and the client cannot attack faster than the game allows.</p>
 */
public final class AutoClickerModule extends Module {

	private final EnumSetting<Trigger> trigger = setting(new EnumSetting<>("Trigger",
			"When the clicker may click.", Trigger.HOLD));
	private final RangeSetting cps = setting(new RangeSetting("CPS",
			"Clicks per second, rolled inside this range each time.", 8.0, 12.0, 1.0, 20.0, true, ""));
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

		if (this.leftClick.get()) {
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
