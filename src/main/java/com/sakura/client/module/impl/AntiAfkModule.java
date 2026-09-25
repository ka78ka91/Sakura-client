package com.sakura.client.module.impl;

import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import com.sakura.client.setting.BooleanSetting;
import com.sakura.client.setting.RangeSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Nudges the client every so often so an idle player is not kicked.
 *
 * <p>Servers that kick for inactivity usually watch for the player having done <em>something</em> recently —
 * moved, looked around, swung. Sitting perfectly still for half an hour is the signature they look for, and this
 * breaks it with the smallest possible change: a small camera turn every so many seconds, with a randomised
 * interval and a randomised direction.</p>
 *
 * <p>Only genuine player state is touched — yaw and pitch — so nothing here is a packet the client would not
 * otherwise send, and the server sees a player who occasionally shifts in their seat. It is off by default
 * because doing this unattended is exactly the kind of automation that gets an account flagged.</p>
 *
 * <h2>Why the interval and the direction are random</h2>
 *
 * <p>A nudge every exactly thirty seconds is easier to spot than no nudge at all, because the pattern is
 * unmistakably mechanical. The interval is therefore drawn from a range, and each nudge turns a random way so
 * two in a row do not cancel out into a view that never actually moved.</p>
 */
public final class AntiAfkModule extends Module {

	/**
	 * How far a nudge may turn the camera, in degrees.
	 *
	 * <p>Small on purpose: the point is to register as activity, not to move the player's view somewhere they
	 * did not ask to look.</p>
	 */
	private static final float TURN_DEGREES = 12.0f;
	private static final float PITCH_DEGREES = 5.0f;

	private final BooleanSetting turn = setting(new BooleanSetting("Turn",
			"Occasionally turn the camera a little.", true));
	private final RangeSetting interval = setting(new RangeSetting("Interval",
			"Seconds between two nudges, rolled inside this range.", 20.0, 45.0, 5.0, 300.0, true, "s"));

	/** When the next nudge is due, in milliseconds, or {@code 0} while one has not been scheduled yet. */
	private long nextNudgeAt;

	public AntiAfkModule() {
		super("AntiAFK", Category.MISC, "Makes small movements so an idle player is not kicked.");
	}

	@Override
	public void onDisable() {
		this.nextNudgeAt = 0L;
	}

	@Override
	public void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;

		if (!isEnabled() || player == null || client.currentScreen != null) {
			return;
		}

		long now = System.currentTimeMillis();

		if (this.nextNudgeAt == 0L) {
			// Schedule the first nudge rather than acting immediately, so switching the module on does not jerk
			// the camera before the player has even let go of the menu.
			this.nextNudgeAt = now + nextIntervalMillis();
			return;
		}

		if (now < this.nextNudgeAt) {
			return;
		}

		if (this.turn.get()) {
			float yawStep = (float) ThreadLocalRandom.current().nextDouble(-TURN_DEGREES, TURN_DEGREES);
			float pitchStep = (float) ThreadLocalRandom.current().nextDouble(-PITCH_DEGREES, PITCH_DEGREES);

			player.setYaw(player.getYaw() + yawStep);
			player.setPitch(Math.clamp(player.getPitch() + pitchStep, -90.0f, 90.0f));
		}

		this.nextNudgeAt = now + nextIntervalMillis();
	}

	/** @return milliseconds until the next nudge, drawn from the configured range */
	private long nextIntervalMillis() {
		return Math.round(this.interval.random() * 1000.0);
	}
}
