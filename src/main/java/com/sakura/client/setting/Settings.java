package com.sakura.client.setting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serialisation helpers turning a module's settings into the flat string-to-value maps stored in the config
 * file, and back.
 *
 * <p>Kept separate from {@link Setting} so the setting classes stay free of any file or Gson knowledge.</p>
 */
public final class Settings {

	private Settings() {
	}

	/** @return setting name to raw value, ready to be written to the config file */
	public static Map<String, Object> toMap(List<Setting<?>> settings) {
		Map<String, Object> map = new LinkedHashMap<>();

		for (Setting<?> setting : settings) {
			map.put(setting.getName(), setting.toConfig());
		}

		return map;
	}

	/**
	 * Applies values read from the config file. Unknown names are ignored and malformed values are rejected by
	 * each setting, so a config written by an older version cannot break the client.
	 */
	public static void applyMap(List<Setting<?>> settings, Map<String, Object> raw) {
		if (raw == null) {
			return;
		}

		for (Setting<?> setting : settings) {
			Object value = raw.get(setting.getName());

			if (value == null) {
				continue;
			}

			try {
				setting.fromConfig(value);
			} catch (RuntimeException exception) {
				// A single bad entry must not stop the rest of the module from loading.
				org.slf4j.LoggerFactory.getLogger("sakura/settings").warn(
						"Ignoring invalid value '{}' for setting '{}'", value, setting.getName(), exception);
			}
		}
	}

	/** Resets every setting of the list to its default. */
	public static void restoreAll(List<Setting<?>> settings) {
		for (Setting<?> setting : settings) {
			setting.restore();
		}
	}
}
