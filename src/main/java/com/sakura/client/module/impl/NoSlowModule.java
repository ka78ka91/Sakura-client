package com.sakura.client.module.impl;

import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import com.sakura.client.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;

/**
 * Removes the movement penalty for using an item.
 *
 * <p>Eating, drinking, blocking with a shield and drawing a bow all cut the player's speed, and vanilla applies
 * that cut to the movement input rather than to the item itself. Skipping the cut is therefore a change to how
 * the client reports its own movement, which is exactly the kind of thing a movement check notices: a player who
 * walks at full speed while eating is doing something the unmodified client cannot do.</p>
 *
 * <p>That is why this is rated risky and off by default, and why it is implemented as narrowly as possible —
 * only the speed factors are skipped, not the item use, not the pose, and not anything that decides whether the
 * action is allowed to happen at all.</p>
 *
 * <h2>The hook</h2>
 *
 * <p>{@code ClientPlayerEntity.applyMovementSpeedFactors} is the single place vanilla multiplies the movement
 * vector down for item use, sneaking and the other slow states. Cancelling it means the vector passes through
 * untouched, which leaves every other part of the tick — the pose, the jump flag, the render angles — exactly as
 * vanilla computed it.</p>
 *
 * <p>Only the item-use penalties are removed, and only for the categories the player switched on: the method
 * also applies the sneaking penalty, and cancelling that would make the module a speed module, which it is
 * explicitly not.</p>
 */
public final class NoSlowModule extends Module {

	private final BooleanSetting whileEating = setting(new BooleanSetting("While Eating",
			"Ignore the slowdown from eating and drinking.", true));
	private final BooleanSetting whileBlocking = setting(new BooleanSetting("While Blocking",
			"Ignore the slowdown from holding up a shield.", true));
	private final BooleanSetting whileDrawing = setting(new BooleanSetting("While Drawing",
			"Ignore the slowdown from drawing a bow or charging a trident.", true));

	/** The registered instance, so the mixin can reach the settings without a registry lookup per call. */
	private static NoSlowModule instance;

	/** Whether the item use currently in progress is one the player asked to ignore. */
	private boolean active;

	public NoSlowModule() {
		super("NoSlow", Category.MOVEMENT, "Keeps your speed while eating, blocking or drawing.");

		instance = this;
	}

	/**
	 * @return whether the movement input should pass through unmodified right now
	 *
	 * <p>Called from the mixin on the client tick thread, just before the speed factors would be applied. It
	 * answers from the player's current use state rather than from a key, so the penalty disappears only while an
	 * action that was switched on is actually running.</p>
	 */
	public static boolean shouldSkipSlowdown() {
		NoSlowModule module = instance;

		return module != null && module.isEnabled() && module.active;
	}

	@Override
	public void onTick() {
		this.active = isEnabled() && currentUseIsIgnored();
	}

	@Override
	public void onDisable() {
		this.active = false;
	}

	/** Reads which kind of item use is in progress, if any, and whether that kind is switched on. */
	private boolean currentUseIsIgnored() {
		ClientPlayerEntity player = MinecraftClient.getInstance().player;

		if (player == null || !player.isUsingItem()) {
			return false;
		}

		ItemStack stack = player.getActiveItem();
		Item item = stack.getItem();

		if (item instanceof ShieldItem) {
			return this.whileBlocking.get();
		}

		// A drawn bow and a charged trident both go through the same use action, so they share one switch.
		if (item == Items.BOW || item == Items.TRIDENT || item == Items.CROSSBOW) {
			return this.whileDrawing.get();
		}

		if (item == Items.POTION || stack.contains(DataComponentTypes.FOOD)) {
			return this.whileEating.get();
		}

		return false;
	}
}
