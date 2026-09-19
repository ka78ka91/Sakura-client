package com.sakura.client.rotation;

import com.sakura.client.setting.BooleanSetting;
import com.sakura.client.setting.EnumSetting;
import com.sakura.client.setting.NumberSetting;

/**
 * The rotation settings of one module, as live views onto that module's {@link com.sakura.client.setting.Setting}
 * objects.
 *
 * <p>Each rotating module owns its own speed and smoothing rather than sharing a global one, which is how
 * LiquidBounce's per-module rotation groups work and the only way a silent kill aura and a visible aim assist can
 * coexist with different limits.</p>
 */
public final class RotationSettings {

	private final EnumSetting<RotationMode> mode;
	private final NumberSetting speed;
	private final BooleanSetting silent;
	private final NumberSetting resetThreshold;
	private final NumberSetting ticksUntilReset;

	public RotationSettings(EnumSetting<RotationMode> mode, NumberSetting speed, BooleanSetting silent,
							NumberSetting resetThreshold, NumberSetting ticksUntilReset) {
		this.mode = mode;
		this.speed = speed;
		this.silent = silent;
		this.resetThreshold = resetThreshold;
		this.ticksUntilReset = ticksUntilReset;
	}

	public RotationMode mode() {
		return this.mode.get();
	}

	/** @return degrees of travel per tick, which sets how long a turn of a given size takes */
	public float speed() {
		return (float) this.speed.get().doubleValue();
	}

	/** @return true to keep the rotation off the client's own camera and only in outgoing packets */
	public boolean silent() {
		return this.silent.get();
	}

	/** @return how close to the target counts as arrived, in degrees */
	public float resetThreshold() {
		return (float) this.resetThreshold.get().doubleValue();
	}

	/** @return ticks to keep aiming after the module stops asking, before the view returns to the player */
	public int ticksUntilReset() {
		return this.ticksUntilReset.intValue();
	}
}
