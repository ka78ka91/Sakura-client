package com.sakura.client.setting;

/**
 * How likely a mode is to be noticed by a server's anticheat.
 *
 * <p>Modules ported from LiquidBounce carry modes that target specific anticheat builds. Those are often
 * patched within weeks, so every mode states its own risk and the settings panel shows the label.</p>
 */
public enum Risk {

	/** Uses only legitimate game mechanics, e.g. jumping to land a critical hit. */
	SAFE("safe"),

	/** Manipulates packets or movement in ways anticheats watch for, but is not a known signature. */
	RISKY("risky"),

	/** Targets a specific anticheat build and is very likely already patched. */
	OUTDATED("outdated");

	private final String label;

	Risk(String label) {
		this.label = label;
	}

	public String getLabel() {
		return this.label;
	}
}
