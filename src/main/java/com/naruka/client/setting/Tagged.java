package com.naruka.client.setting;

/**
 * Implemented by enum constants that back an {@link EnumSetting} so they can carry a display name and a
 * {@link Risk} label.
 *
 * <p>Equivalent to LiquidBounce's {@code Tagged} interface (GPL-3.0).</p>
 */
public interface Tagged {

	/** Display name shown in the dropdown, e.g. {@code JumpReset}. */
	String getTag();

	/** Defaults to {@link Risk#SAFE}; modes that spoof packets should override it. */
	default Risk getRisk() {
		return Risk.SAFE;
	}
}
