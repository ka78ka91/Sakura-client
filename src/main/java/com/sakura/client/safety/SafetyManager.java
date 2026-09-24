package com.sakura.client.safety;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pre-emptive safety layer: how often each module is allowed to act.
 *
 * <p>{@link FlagDetector} is the reactive half of the client's safety story — it notices the server correcting
 * our position and pauses the modules that manipulate movement. This is the other half. It never reacts to
 * anything: it simply refuses to let one module produce more actions per second than a person plausibly could,
 * so a runaway loop or a mis-set rate can never turn into a burst the server would see as machine-generated.</p>
 *
 * <h2>What "an action" means</h2>
 *
 * <p>Each module decides what to count and calls {@link #recordAction(String)} after it has actually done
 * something observable — a click sent, an attack issued, an inventory slot moved. Asking first with
 * {@link #canAct(String)} is the contract: a module that checks and then acts cannot exceed its budget, and one
 * that only records is still counted for the diagnostics.</p>
 *
 * <h2>Why it can only ever make the client quieter</h2>
 *
 * <p>Exhausting a budget does not raise anything, disable anything, or change a module's own settings: the
 * module is told "not this tick" and tries again on the next one. The failure mode is therefore a client that
 * acts a little more slowly than configured, never one that acts faster or in a way it did not otherwise.</p>
 *
 * <p>State is per session and deliberately not persisted. A budget is a rate limit, not a preference, and a
 * stale allowance loaded from disk would be worse than the defaults: it would arrive without any of the
 * context that produced it.</p>
 */
public final class SafetyManager {

	/** The window every budget is measured over. One second is the unit anticheats themselves count in. */
	private static final long WINDOW_MILLIS = 1000L;

	/**
	 * Actions per second a module may take when it has not asked for its own allowance.
	 *
	 * <p>Twenty matches the client's tick rate, so a module acting on every tick is exactly at its default and
	 * nothing that exists today is silently slowed down; the budget only bites on modules that were already
	 * doing more than one thing per tick, which is where a burst can come from.</p>
	 */
	public static final int DEFAULT_BUDGET = 20;

	/** Per-module allowances, and the timestamp of every action each one has taken inside the window. */
	private static final Map<String, Integer> BUDGETS = new ConcurrentHashMap<>();
	private static final Map<String, Deque<Long>> ACTIONS = new ConcurrentHashMap<>();

	private SafetyManager() {
	}

	// ------------------------------------------------------------------- ticking

	/** Advances the layer. Called once per client tick, next to {@link FlagDetector#tick()}. */
	public static void tick() {
		// Nothing to advance: the window is evaluated from the timestamps themselves, so a module that has
		// stopped acting simply falls out of its own window without any work here.
	}

	// ---------------------------------------------------------------- allowance

	/** Gives {@code module} its own allowance, in actions per second. Values below one are clamped to one. */
	public static void setBudget(String module, int actionsPerSecond) {
		BUDGETS.put(module, Math.max(1, actionsPerSecond));
	}

	/** @return the allowance {@code module} is currently held to */
	public static int getBudget(String module) {
		return BUDGETS.getOrDefault(module, DEFAULT_BUDGET);
	}

	/**
	 * @return whether {@code module} may act right now, without recording anything
	 *
	 * <p>A module that only asks is never charged, so this is safe to call for a dry run such as drawing an
	 * indicator.</p>
	 */
	public static boolean canAct(String module) {
		return canAct(module, 1);
	}

	/**
	 * @param cost how many actions the caller is about to spend, for an operation that is more than one
	 * @return whether the module still has room for that many actions inside the window
	 */
	public static boolean canAct(String module, int cost) {
		int budget = getBudget(module);
		return used(module, System.currentTimeMillis()) + Math.max(1, cost) <= budget;
	}

	/**
	 * Charges {@code module} for one action.
	 *
	 * <p>Call it after the action has been taken, not before: a charge for something that then failed to happen
	 * would count against the module for no reason.</p>
	 */
	public static void recordAction(String module) {
		long now = System.currentTimeMillis();
		Deque<Long> actions = ACTIONS.computeIfAbsent(module, key -> new ArrayDeque<>());
		actions.addLast(now);
		prune(actions, now);
	}

	// -------------------------------------------------------------- diagnostics

	/** @return actions {@code module} has taken in the last second */
	public static int used(String module) {
		return used(module, System.currentTimeMillis());
	}

	/** @return the allowance still available to {@code module} right now */
	public static int remaining(String module) {
		return Math.max(0, getBudget(module) - used(module));
	}

	/** @return a one-line summary of every module that has acted, for a log or a debug overlay */
	public static String describe() {
		StringBuilder text = new StringBuilder();

		for (String module : ACTIONS.keySet()) {
			if (text.length() > 0) {
				text.append(", ");
			}

			text.append(module).append(' ').append(used(module)).append('/').append(getBudget(module));
		}

		return text.length() == 0 ? "idle" : text.toString();
	}

	/** Drops every allowance and counter. Used when the configuration is replaced under the client. */
	public static void reset() {
		ACTIONS.clear();
		BUDGETS.clear();
	}

	// ------------------------------------------------------------------ internals

	private static int used(String module, long now) {
		Deque<Long> actions = ACTIONS.get(module);

		if (actions == null) {
			return 0;
		}

		prune(actions, now);
		return actions.size();
	}

	/** Drops the timestamps that have fallen out of the window, so the deque size is the current rate. */
	private static void prune(Deque<Long> actions, long now) {
		while (!actions.isEmpty() && now - actions.peekFirst() >= WINDOW_MILLIS) {
			actions.removeFirst();
		}
	}
}
