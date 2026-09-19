package com.naruka.client;

import com.naruka.client.config.ConfigManager;
import com.naruka.client.config.NarukaConfig;
import com.naruka.client.gui.ClickGuiScreen;
import com.naruka.client.gui.HudEditorScreen;
import com.naruka.client.hud.HudManager;
import com.naruka.client.hud.HudRenderer;
import com.naruka.client.hud.element.CoordinatesElement;
import com.naruka.client.hud.element.FpsElement;
import com.naruka.client.hud.element.KeystrokesElement;
import com.naruka.client.hud.element.ModuleListElement;
import com.naruka.client.hud.element.MusicPlayerElement;
import com.naruka.client.module.Module;
import com.naruka.client.module.ModuleManager;
import com.naruka.client.module.impl.AutoClickerModule;
import com.naruka.client.module.impl.FullbrightModule;
import com.naruka.client.module.impl.HitboxModule;
import com.naruka.client.module.impl.KeepSprintModule;
import com.naruka.client.module.impl.NoMissCooldownModule;
import com.naruka.client.module.impl.ReachModule;
import com.naruka.client.module.impl.TriggerBotModule;
import com.naruka.client.notification.NotificationManager;
import com.naruka.client.rotation.RotationManager;
import com.naruka.client.safety.FlagDetector;
import com.naruka.client.setting.Setting;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Client entry point.
 *
 * <p>Start-up order matters: the config is read first so module state and key binds can be applied
 * immediately, then modules and HUD elements are registered, then persistence is replayed on top of the
 * freshly created defaults.</p>
 */
public class NarukaClient implements ClientModInitializer {

	public static final String MOD_ID = "naruka";
	public static final String MOD_NAME = "Naruka Client";

	private static final Set<Integer> KEYS_DOWN_LAST_TICK = new HashSet<>();

	private static KeyBinding clickGuiKey;
	private static boolean restored;
	private static boolean settingsDirty;
	/** Set while the config is being applied, so loading values does not immediately mark them dirty. */
	private static boolean loadingSettings;

	@Override
	public void onInitializeClient() {
		ConfigManager.load();

		// Any parameter change marks the config dirty; it is flushed once per tick instead of per edit.
		Setting.setGlobalChangeListener(setting -> {
			if (!loadingSettings) {
				settingsDirty = true;
			}
		});
		Module.setToggleListener(NotificationManager::showModuleToggle);

		// Feature modules.
		ModuleManager.register(new AutoClickerModule());
		ModuleManager.register(new FullbrightModule());
		ModuleManager.register(new HitboxModule());
		ModuleManager.register(new KeepSprintModule());
		ModuleManager.register(new NoMissCooldownModule());
		ModuleManager.register(new ReachModule());
		ModuleManager.register(new TriggerBotModule());

		// HUD elements are modules too, so they can be toggled and bound like anything else.
		HudManager.register(new MusicPlayerElement());
		HudManager.register(new ModuleListElement());
		HudManager.register(new KeystrokesElement());
		HudManager.register(new CoordinatesElement());
		HudManager.register(new FpsElement());

		HudRenderer.register();

		clickGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.naruka.clickgui",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				KeyBinding.Category.MISC));

		ClientTickEvents.END_CLIENT_TICK.register(NarukaClient::onEndClientTick);
	}

	/**
	 * Restores the saved module state and HUD layout.
	 *
	 * <p>Deliberately deferred to the first client tick rather than done at mod-init time: Fabric runs
	 * client entrypoints from inside the {@code MinecraftClient} constructor, where
	 * {@code MinecraftClient.getInstance().options} is still {@code null}. Enabling a module there would
	 * crash on start-up as soon as the config had one switched on.</p>
	 */
	private static void restorePersistedState() {
		NarukaConfig config = ConfigManager.get();
		ModuleManager.applyPersistedState(config.moduleStates, config.moduleKeybinds);
		applyModuleSettings(config);
		HudManager.loadPositions();
	}

	/** Applies the per-module parameter values stored in the config. */
	private static void applyModuleSettings(NarukaConfig config) {
		loadingSettings = true;

		try {
			for (Module module : ModuleManager.getAll()) {
				module.applySettings(config.moduleSettings.get(module.getName()));
			}
		} finally {
			loadingSettings = false;
		}
	}

	/**
	 * Writes every module's parameters back to the config file.
	 *
	 * <p>Called once per client tick and returns immediately unless a setting actually changed, so dragging a
	 * slider does not touch the disk on every frame.</p>
	 */
	public static void persistModuleSettings() {
		if (!settingsDirty) {
			return;
		}

		NarukaConfig config = ConfigManager.get();

		for (Module module : ModuleManager.getAll()) {
			Map<String, Object> values = module.settingsToMap();

			if (!values.isEmpty()) {
				config.moduleSettings.put(module.getName(), values);
			}
		}

		ConfigManager.save();
		settingsDirty = false;
	}

	private static void onEndClientTick(MinecraftClient client) {
		if (!restored) {
			restored = true;
			restorePersistedState();
		}

		if (clickGuiKey.wasPressed()) {
			toggleMenu(client);
		}

		NotificationManager.tick();
		FlagDetector.tick();
		RotationManager.tick(client.player);
		persistModuleSettings();
		pollModuleKeybinds(client);
		ModuleManager.tick();
	}

	private static void toggleMenu(MinecraftClient client) {
		if (client.currentScreen instanceof ClickGuiScreen || client.currentScreen instanceof HudEditorScreen) {
			client.setScreen(null);
		} else if (client.currentScreen == null) {
			client.setScreen(new ClickGuiScreen());
		}
	}

	/**
	 * Toggles modules bound to a raw key code.
	 *
	 * <p>Key binds are stored as plain GLFW codes, so they cannot go through
	 * {@code KeyBinding.wasPressed()}; the edge is detected from {@link InputUtil#isKeyPressed} instead.
	 * Bindings are ignored while any screen is open, so typing in a menu never toggles a module.</p>
	 */
	private static void pollModuleKeybinds(MinecraftClient client) {
		if (client.currentScreen != null || client.getWindow() == null) {
			KEYS_DOWN_LAST_TICK.clear();
			return;
		}

		Window window = client.getWindow();
		boolean changed = false;

		for (Module module : ModuleManager.getAll()) {
			if (!module.hasKeybind()) {
				continue;
			}

			int code = module.getKeybind();
			boolean down = InputUtil.isKeyPressed(window, code);
			boolean wasDown = KEYS_DOWN_LAST_TICK.contains(code);

			if (down && !wasDown && ModuleManager.handleKeybind(code)) {
				changed = true;
			}

			if (down) {
				KEYS_DOWN_LAST_TICK.add(code);
			} else {
				KEYS_DOWN_LAST_TICK.remove(code);
			}
		}

		if (changed) {
			persistModuleStates();
		}
	}

	private static void persistModuleStates() {
		NarukaConfig config = ConfigManager.get();

		for (Module module : ModuleManager.getAll()) {
			config.moduleStates.put(module.getName(), module.isEnabled());
		}

		ConfigManager.save();
	}
}
