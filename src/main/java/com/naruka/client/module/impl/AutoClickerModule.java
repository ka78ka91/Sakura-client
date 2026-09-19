package com.naruka.client.module.impl;

import com.naruka.client.mixin.MinecraftClientAccessor;
import com.naruka.client.module.Category;
import com.naruka.client.module.Module;
import com.naruka.client.safety.Clicker;
import com.naruka.client.setting.BooleanSetting;
import com.naruka.client.setting.EnumSetting;
import com.naruka.client.setting.RangeSetting;
import com.naruka.client.setting.Tagged;
import net.minecraft.client.MinecraftClient;

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
			((MinecraftClientAccessor) client).naruka$doAttack();
			clicked = true;
		}

		if (this.rightClick.get()) {
			((MinecraftClientAccessor) client).naruka$doItemUse();
			clicked = true;
		}

		if (clicked) {
			this.clicker.registerClick(now);
			this.nextClickAt = now + delay();
		}
	}

	/** @return milliseconds until the next click, rolled inside the configured range */
	private long delay() {
		return 1000L / Math.max(1, this.cps.randomInt());
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
