package com.naruka.client.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central registry for every {@link Module}.
 *
 * <p>Modules are registered once during client initialisation. Registration order defines the
 * display order inside each category, so it is kept insertion-ordered rather than sorted.</p>
 */
public final class ModuleManager {

	private static final List<Module> MODULES = new ArrayList<>();
	private static final Map<String, Module> BY_NAME = new LinkedHashMap<>();

	private ModuleManager() {
	}

	public static void register(Module module) {
		if (BY_NAME.containsKey(module.getName())) {
			throw new IllegalStateException("Duplicate module name: " + module.getName());
		}

		MODULES.add(module);
		BY_NAME.put(module.getName(), module);
	}

	public static List<Module> getAll() {
		return Collections.unmodifiableList(MODULES);
	}

	public static List<Module> getByCategory(Category category) {
		List<Module> result = new ArrayList<>();

		for (Module module : MODULES) {
			if (module.getCategory() == category) {
				result.add(module);
			}
		}

		return result;
	}

	public static List<Module> getEnabled() {
		List<Module> result = new ArrayList<>();

		for (Module module : MODULES) {
			if (module.isEnabled()) {
				result.add(module);
			}
		}

		return result;
	}

	public static Module get(String name) {
		return BY_NAME.get(name);
	}

	/** Case-insensitive substring search over names and descriptions. */
	public static List<Module> search(String query) {
		if (query == null || query.isBlank()) {
			return Collections.unmodifiableList(MODULES);
		}

		String needle = query.toLowerCase();
		List<Module> result = new ArrayList<>();

		for (Module module : MODULES) {
			if (module.getName().toLowerCase().contains(needle)
					|| module.getDescription().toLowerCase().contains(needle)) {
				result.add(module);
			}
		}

		return result;
	}

	public static void tick() {
		for (Module module : MODULES) {
			if (module.isEnabled()) {
				module.onTick();
			}
		}
	}

	/**
	 * Toggles the module bound to {@code keyCode}, if any.
	 *
	 * @return {@code true} when a module consumed the key
	 */
	public static boolean handleKeybind(int keyCode) {
		boolean handled = false;

		for (Module module : MODULES) {
			if (module.hasKeybind() && module.getKeybind() == keyCode) {
				module.toggle();
				handled = true;
			}
		}

		return handled;
	}

	/** Re-applies persisted state; called after the config has been read. */
	public static void applyPersistedState(Map<String, Boolean> states, Map<String, Integer> keybinds) {
		for (Module module : MODULES) {
			Boolean state = states.get(module.getName());
			module.setEnabled(state != null ? state : module.isEnabledByDefault());

			Integer key = keybinds.get(module.getName());
			module.setKeybind(key != null ? key : Module.UNBOUND);
		}
	}
}
