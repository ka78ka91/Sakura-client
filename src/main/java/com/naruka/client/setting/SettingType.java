package com.naruka.client.setting;

/**
 * Tells the settings panel which widget to build for a {@link Setting}.
 *
 * <p>The panel switches on this instead of using {@code instanceof} chains, so adding a setting type is a
 * compile-time visible change.</p>
 */
public enum SettingType {

	/** On/off switch. */
	BOOLEAN,

	/** Single number picked with a slider. */
	NUMBER,

	/** Two numbers (a min/max pair) with one random value drawn from the range at use time. */
	RANGE,

	/** A 0-100 percentage that is rolled as a chance. */
	CHANCE,

	/** One of several named modes. */
	ENUM,

	/** Any number of named entries, e.g. a target filter. */
	MULTI_CHOICE,

	/** Packed ARGB colour. */
	COLOR
}
