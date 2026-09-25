package com.sakura.client;

import com.sakura.client.config.ConfigManager;
import com.sakura.client.config.SakuraConfig;
import com.sakura.client.gui.ClickGuiScreen;
import com.sakura.client.gui.HudEditorScreen;
import com.sakura.client.hud.HudManager;
import com.sakura.client.hud.HudRenderer;
import com.sakura.client.hud.element.ArmorHudElement;
import com.sakura.client.hud.element.CoordinatesElement;
import com.sakura.client.hud.element.FpsElement;
import com.sakura.client.hud.element.KeystrokesElement;
import com.sakura.client.hud.element.ModuleListElement;
import com.sakura.client.hud.element.MusicPlayerElement;
import com.sakura.client.hud.element.PingTpsElement;
import com.sakura.client.hud.element.PotionHudElement;
import com.sakura.client.hud.element.ServerInfoElement;
import com.sakura.client.hud.element.SessionTimerElement;
import com.sakura.client.hud.element.TargetHudElement;
import com.sakura.client.hud.element.WatermarkElement;
import com.sakura.client.module.Module;
import com.sakura.client.module.ModuleManager;
import com.sakura.client.module.impl.AimbotModule;
import com.sakura.client.module.impl.AntiAfkModule;
import com.sakura.client.module.impl.AutoClickerModule;
import com.sakura.client.module.impl.AutoReconnectModule;
import com.sakura.client.module.impl.AutoRespawnModule;
import com.sakura.client.module.impl.AutoToolModule;
import com.sakura.client.module.impl.AutoTotemModule;
import com.sakura.client.module.impl.AutoWeaponModule;
import com.sakura.client.module.impl.CriticalsModule;
import com.sakura.client.module.impl.EspModule;
import com.sakura.client.module.impl.FastPlaceModule;
import com.sakura.client.module.impl.FullbrightModule;
import com.sakura.client.module.impl.HitboxModule;
import com.sakura.client.module.impl.KeepSprintModule;
import com.sakura.client.module.impl.KillAuraModule;
import com.sakura.client.module.impl.NameProtectModule;
import com.sakura.client.module.impl.NameTagsModule;
import com.sakura.client.module.impl.NoMissCooldownModule;
import com.sakura.client.module.impl.NoSlowModule;
import com.sakura.client.module.impl.ReachModule;
import com.sakura.client.module.impl.SafeWalkModule;
import com.sakura.client.module.impl.SprintModule;
import com.sakura.client.module.impl.TracersModule;
import com.sakura.client.module.impl.TriggerBotModule;
import com.sakura.client.module.impl.VelocityModule;
import com.sakura.client.notification.NotificationManager;
import com.sakura.client.render.WorldOverlayRenderer;
import com.sakura.client.render.WorldProjection;
import com.sakura.client.rotation.RotationManager;
import com.sakura.client.safety.FlagDetector;
import com.sakura.client.safety.SafetyManager;
import com.sakura.client.setting.Setting;
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
public class SakuraClient implements ClientModInitializer {

	public static final String MOD_ID = "sakura";
	public static final String MOD_NAME = "Sakura Client";

	private static final Set<Integer> KEYS_DOWN_LAST_TICK = new HashSet<>();

	/** True while the previous tick saw an open screen; the tick after it closes re-baselines quietly. */
	private static boolean screenWasOpen;

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
		ModuleManager.register(new AimbotModule());
		ModuleManager.register(new AntiAfkModule());
		ModuleManager.register(new AutoClickerModule());
		ModuleManager.register(new AutoReconnectModule());
		ModuleManager.register(new AutoRespawnModule());
		ModuleManager.register(new AutoTotemModule());
		ModuleManager.register(new AutoToolModule());
		ModuleManager.register(new AutoWeaponModule());
		ModuleManager.register(new CriticalsModule());
		ModuleManager.register(new EspModule());
		ModuleManager.register(new FastPlaceModule());
		ModuleManager.register(new FullbrightModule());
		ModuleManager.register(new HitboxModule());
		ModuleManager.register(new KeepSprintModule());
		ModuleManager.register(new KillAuraModule());
		ModuleManager.register(new NameProtectModule());
		ModuleManager.register(new NameTagsModule());
		ModuleManager.register(new NoMissCooldownModule());
		ModuleManager.register(new NoSlowModule());
		ModuleManager.register(new ReachModule());
		ModuleManager.register(new SafeWalkModule());
		ModuleManager.register(new SprintModule());
		ModuleManager.register(new TracersModule());
		ModuleManager.register(new TriggerBotModule());
		ModuleManager.register(new VelocityModule());

		// HUD elements are modules too, so they can be toggled and bound like anything else. None of them
		// is enabled by default: a fresh install renders nothing until the player opts in.
		HudManager.register(new MusicPlayerElement());
		HudManager.register(new ModuleListElement());
		HudManager.register(new KeystrokesElement());
		HudManager.register(new CoordinatesElement());
		HudManager.register(new FpsElement());
		HudManager.register(new TargetHudElement());
		HudManager.register(new ArmorHudElement());
		HudManager.register(new PotionHudElement());
		HudManager.register(new PingTpsElement());
		HudManager.register(new ServerInfoElement());
		HudManager.register(new SessionTimerElement());
		HudManager.register(new WatermarkElement());

		HudRenderer.register();

		// World overlays: the matrix capture feeds the projection the ESP, name tags and tracers draw with.
		WorldProjection.register();
		WorldOverlayRenderer.register();

		clickGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.sakura.clickgui",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				KeyBinding.Category.MISC));

		ClientTickEvents.END_CLIENT_TICK.register(SakuraClient::onEndClientTick);
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
		SakuraConfig config = ConfigManager.get();
		ModuleManager.applyPersistedState(config.moduleStates, config.moduleKeybinds);
		applyModuleSettings(config);
		HudManager.loadPositions();
	}

	/** Applies the per-module parameter values stored in the config. */
	private static void applyModuleSettings(SakuraConfig config) {
		loadingSettings = true;

		try {
			for (Module module : ModuleManager.getAll()) {
				module.applySettings(config.moduleSettings.get(module.getName()));
			}
		} finally {
			loadingSettings = false;
		}
	}

	/** Re-applies per-module parameter values from the live config; used by the GUI after a profile switch. */
	public static void applyModuleSettings() {
		applyModuleSettings(ConfigManager.get());
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

		SakuraConfig config = ConfigManager.get();

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
		RegistryAudit.runOnce();

		if (!restored) {
			restored = true;
			restorePersistedState();
		}

		if (clickGuiKey.wasPressed()) {
			toggleMenu(client);
		}

		NotificationManager.tick();
		FlagDetector.tick();
		SafetyManager.tick();
		persistModuleSettings();
		pollModuleKeybinds(client);

		// Modules first, rotation last: a module that aims during its own tick has already placed its request
		// by the time the manager runs, so the turn it produces belongs to this tick instead of the next one.
		ModuleManager.tick();
		RotationManager.tick(client.player);
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
			screenWasOpen = true;
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

			if (screenWasOpen) {
				// The first tick after a screen closes re-baselines instead of firing: a key held inside
				// the menu (the GUI key itself, a movement key) would otherwise read as a fresh press the
				// moment the screen is gone and toggle its module without the player ever releasing it.
				if (down) {
					KEYS_DOWN_LAST_TICK.add(code);
				} else {
					KEYS_DOWN_LAST_TICK.remove(code);
				}

				continue;
			}

			if (down && !wasDown && ModuleManager.handleKeybind(code)) {
				changed = true;
			}

			if (down) {
				KEYS_DOWN_LAST_TICK.add(code);
			} else {
				KEYS_DOWN_LAST_TICK.remove(code);
			}
		}

		screenWasOpen = false;

		if (changed) {
			persistModuleStates();
		}
	}

	private static void persistModuleStates() {
		SakuraConfig config = ConfigManager.get();

		for (Module module : ModuleManager.getAll()) {
			config.moduleStates.put(module.getName(), module.isEnabled());
		}

		ConfigManager.save();
	}
}
