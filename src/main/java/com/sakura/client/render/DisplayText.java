package com.sakura.client.render;

/**
 * A hook that lets a module rewrite strings on their way to the screen.
 *
 * <p>It exists so {@link RenderUtils} does not have to know which module wants the text changed. The render
 * package sits underneath the modules — every HUD element and every world overlay draws through it — so a
 * direct reference from here to a module would point the dependency the wrong way and make the two packages
 * impossible to separate later. Instead a module installs itself here at construction and the renderer asks a
 * single question.</p>
 *
 * <p>Exactly one replacer can be installed at a time; installing another replaces it. That is deliberate: two
 * modules rewriting the same string would produce whatever the registration order happened to be, which is a
 * bug that only shows up when both are switched on. One slot makes the conflict impossible.</p>
 */
public final class DisplayText {

	/** Rewrites {@code text} for display. Replaced by a module when it is constructed. */
	@FunctionalInterface
	public interface Replacer {

		/**
		 * @param text the string about to be drawn
		 * @return what should actually be drawn, or the same string when nothing applies
		 */
		String replace(String text);
	}

	private static volatile Replacer replacer;

	private DisplayText() {
	}

	/**
	 * Installs {@code next} as the replacer.
	 *
	 * @param next the replacer, or {@code null} to remove the current one
	 */
	public static void set(Replacer next) {
		replacer = next;
	}

	/**
	 * @return the string to draw for {@code text}, unchanged when no replacer is installed or none applies
	 */
	public static String apply(String text) {
		Replacer current = replacer;

		if (current == null || text == null || text.isEmpty()) {
			return text;
		}

		String replaced = current.replace(text);
		return replaced == null ? text : replaced;
	}
}
