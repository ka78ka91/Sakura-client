package com.sakura.client;

import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import com.sakura.client.module.ModuleManager;
import com.sakura.client.setting.Setting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One-shot start-up audit of the module registry.
 *
 * <p>Printed once, on the first client tick, at info level. It exists because a module that fails to register
 * or that declares a malformed parameter produces no error at all — the client starts, the menu opens, and the
 * only symptom is a missing row that nobody notices until they go looking for it. Registration happens during
 * {@code onInitializeClient}, so by the first tick the registry is final and every setting has been constructed
 * and clamped.</p>
 *
 * <p>This is diagnostic scaffolding: delete this class and the single call in {@link SakuraClient} once the
 * module set is settled.</p>
 */
final class RegistryAudit {

	private static final Logger LOGGER = LoggerFactory.getLogger("sakura/registry");

	private static boolean done;

	private RegistryAudit() {
	}

	/**
	 * Whether to print. Off by default so a normal start-up stays quiet; run once with
	 * {@code -Dsakura.debug.registry=true} to audit the registry, and delete the class once the module set is
	 * settled.
	 */
	private static final boolean ENABLED = Boolean.getBoolean("sakura.debug.registry");

	/** Prints the audit once; later calls do nothing. */
	static void runOnce() {
		if (done) {
			return;
		}

		done = true;

		if (!ENABLED) {
			return;
		}

		StringBuilder report = new StringBuilder();
		report.append("registered modules: ").append(ModuleManager.getAll().size());

		for (Category category : Category.values()) {
			int count = ModuleManager.getByCategory(category).size();
			report.append(System.lineSeparator()).append("  ").append(category.getDisplayName())
					.append(": ").append(count);
		}

		for (Module module : ModuleManager.getAll()) {
			report.append(System.lineSeparator()).append("  ").append(module.getName())
					.append(" [").append(module.getCategory().getDisplayName()).append(']')
					.append(" default=").append(module.isEnabledByDefault())
					.append(" settings=").append(module.getSettings().size());

			for (Setting<?> setting : module.getSettings()) {
				report.append(System.lineSeparator()).append("      ").append(setting.getName())
						.append(" = ").append(setting.displayValue())
						.append(" (").append(setting.type()).append(')');
			}
		}

		LOGGER.info("{}", report);
	}
}
