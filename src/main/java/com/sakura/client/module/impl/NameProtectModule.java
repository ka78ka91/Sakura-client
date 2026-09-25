package com.sakura.client.module.impl;

import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import com.sakura.client.render.DisplayText;
import com.sakura.client.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;

/**
 * Hides the local player's own name wherever the client draws it.
 *
 * <p>Intended for recordings and screenshots: the player's name in their own HUD and in the name tags the client
 * draws is the one thing that gives away who is recording. The replacement is applied at the single point every
 * HUD and world-overlay string passes through, so it covers the module list, the target HUD, the name tags and
 * anything added later without each element having to opt in.</p>
 *
 * <h2>What it does not cover, on purpose</h2>
 *
 * <p>Only strings the client draws itself are touched. Chat, the tab list, the death screen, the menus and
 * anything else vanilla renders are left alone: replacing those means hooking the whole text pipeline, which
 * would also rewrite the player's own outgoing commands and chat, and would interact with the font resource pack
 * and every other screen. That is a far larger change than the problem needs — the name in the tab list is not
 * what a screen recording shows — so it is not attempted here. Anywhere the real name still appears is a
 * deliberate limit rather than an oversight, and it is worth knowing before relying on this while recording.</p>
 *
 * <p>The match is exact: a string is only rewritten when it <em>is</em> the player's name, so a module called
 * "Notch" or a HUD row that merely contains the name is not mangled. It is off by default, since replacing text
 * silently is exactly the kind of thing a player should have asked for.</p>
 */
public final class NameProtectModule extends Module {

	/**
	 * What the name becomes when it is not being hidden outright.
	 *
	 * <p>A fixed word rather than a configurable string because this client has no text-entry setting type, and
	 * adding one to serve a single field would mean a new widget, a new serialisation case and a new validation
	 * path. The alternative — hiding the name entirely — is available as a switch, so the configurable middle
	 * ground is the part that was left out.</p>
	 */
	private static final String REPLACEMENT = "Sakura";

	private final BooleanSetting hideEntirely = setting(new BooleanSetting("Hide Entirely",
			"Draw nothing where the name would be, instead of the stand-in text.", false));

	public NameProtectModule() {
		super("NameProtect", Category.MISC, "Hides your own name in the HUD and in name tags.");

		DisplayText.set(this::protect);
	}

	/**
	 * @param text the string about to be drawn
	 * @return the replacement when {@code text} is the local player's name, otherwise the input unchanged
	 *
	 * <p>Installed once at construction and consulted for every string, so it checks {@link #isEnabled()} itself
	 * rather than being registered and unregistered — an install/uninstall pair would have to be kept in step
	 * with every path that can switch a module on, and would silently stop working the first time one was
	 * missed.</p>
	 */
	private String protect(String text) {
		if (!isEnabled()) {
			return text;
		}

		String name = localName();

		if (name == null || !name.equals(text)) {
			// Exact match only: a substring rule would rewrite every module name and HUD row that happens to
			// contain the player's name.
			return text;
		}

		return this.hideEntirely.get() ? "" : REPLACEMENT;
	}

	/** @return the session's player name, or {@code null} while the client has no session yet */
	private String localName() {
		MinecraftClient client = MinecraftClient.getInstance();

		if (client == null) {
			return null;
		}

		Session session = client.getSession();

		if (session == null || session.getUsername() == null) {
			return null;
		}

		return session.getUsername();
	}
}
