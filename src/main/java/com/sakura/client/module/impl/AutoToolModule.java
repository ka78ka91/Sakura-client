package com.sakura.client.module.impl;

import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import com.sakura.client.setting.BooleanSetting;
import com.sakura.client.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;

/**
 * Switches to the best tool for the block being mined.
 *
 * <p>Only the selected slot changes, and only while the player is actually mining a block. The slot is put back
 * once the block stops being mined, so the hotbar returns to whatever they were holding without them having to
 * think about it.</p>
 *
 * <p>Nothing is hidden from the server: changing the selected slot is the same thing as pressing a hotbar key,
 * and the held-slot update goes out before the breaking packet because vanilla's own interaction manager sends
 * it that way. Rated safe for that reason — the module cannot make the player break a block faster than their
 * best tool allows.</p>
 *
 * <h2>How the best tool is chosen</h2>
 *
 * <p>By the mining speed the stack itself reports for that block, which is the value vanilla multiplies the
 * breaking progress by. That naturally prefers the correct tool over a merely fast one, because only the correct
 * tool gets the correct-tool multiplier. The comparison starts from the empty hand's speed, so a block no tool
 * helps with leaves the player alone. Ties keep the player's own choice rather than shuffling equivalent tools.</p>
 */
public final class AutoToolModule extends Module {

	/** The registered module, so the attack hook can reach it without a lookup. */
	private static AutoToolModule instance;

	private final BooleanSetting switchBack = setting(new BooleanSetting("Switch Back",
			"Put the item you were holding back once the block stops being mined.", true));
	private final NumberSetting switchBackDelay = setting(new NumberSetting("Switch Back Delay",
			"How long to keep the tool out after mining stops.", 200.0, 0.0, 2000.0, 50.0, "ms"));
	private final BooleanSetting requireCorrectTool = setting(new BooleanSetting("Require Correct Tool",
			"Only switch to a tool that can actually harvest the block.", true));

	/** Slot the player was holding when the module took over, or {@code -1} while it is not holding a switch. */
	private int originalSlot = -1;
	/** Slot the module switched to, which is what it restores from. */
	private int toolSlot = -1;
	/** When a block was last mined, which is what decides when the tool can go back. */
	private long lastMinedAt;

	public AutoToolModule() {
		super("AutoTool", Category.PLAYER, "Holds the best tool for the block you are mining.");

		this.switchBackDelay.visibleWhen(this.switchBack::get);
		instance = this;
	}

	/**
	 * Called from the attack hook when the crosshair is on a block, before the breaking packet is sent.
	 *
	 * <p>Returns immediately unless the module is on, so the normal attack path is untouched whenever the module
	 * has nothing to say.</p>
	 */
	public static void beforeMine(MinecraftClient client, BlockHitResult hit) {
		AutoToolModule module = instance;

		if (module == null || !module.isEnabled() || client.player == null || client.world == null) {
			return;
		}

		module.selectTool(client, hit);
	}

	@Override
	public void onDisable() {
		restore(MinecraftClient.getInstance());
	}

	private void selectTool(MinecraftClient client, BlockHitResult hit) {
		ClientPlayerEntity player = client.player;

		if (client.world == null) {
			return;
		}

		BlockState state = client.world.getBlockState(hit.getBlockPos());

		if (state.isAir()) {
			return;
		}

		this.lastMinedAt = System.currentTimeMillis();

		PlayerInventory inventory = player.getInventory();
		int current = inventory.getSelectedSlot();
		int best = bestSlot(player, state);

		if (best < 0 || best == current) {
			return;
		}

		if (this.toolSlot != current) {
			// Whatever is held now is the player's own choice, so this is where a switch has to return to.
			this.originalSlot = current;
		}

		this.toolSlot = best;
		inventory.setSelectedSlot(best);
	}

	/** @return the hotbar slot whose stack mines {@code state} fastest, or {@code -1} when nothing beats the hand */
	private int bestSlot(ClientPlayerEntity player, BlockState state) {
		PlayerInventory inventory = player.getInventory();
		double baseline = ItemStack.EMPTY.getMiningSpeedMultiplier(state);
		double bestSpeed = baseline;
		int best = -1;

		for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
			ItemStack stack = inventory.getStack(slot);

			if (stack.isEmpty()) {
				continue;
			}

			if (this.requireCorrectTool.get() && !stack.isSuitableFor(state)) {
				continue;
			}

			double speed = stack.getMiningSpeedMultiplier(state);

			if (speed > bestSpeed) {
				bestSpeed = speed;
				best = slot;
			}
		}

		return best;
	}

	@Override
	public void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();

		if (!isEnabled() || client.player == null) {
			forget();
			return;
		}

		if (this.toolSlot < 0 || this.originalSlot < 0) {
			return;
		}

		if (client.player.getInventory().getSelectedSlot() != this.toolSlot) {
			// The player picked another slot by hand; the memory is stale and the module must not fight them.
			forget();
			return;
		}

		if (!this.switchBack.get()) {
			return;
		}

		if (System.currentTimeMillis() - this.lastMinedAt < this.switchBackDelay.intValue()) {
			return;
		}

		client.player.getInventory().setSelectedSlot(this.originalSlot);
		forget();
	}

	private void restore(MinecraftClient client) {
		if (this.toolSlot >= 0 && this.originalSlot >= 0 && client.player != null
				&& client.player.getInventory().getSelectedSlot() == this.toolSlot) {
			client.player.getInventory().setSelectedSlot(this.originalSlot);
		}

		forget();
	}

	private void forget() {
		this.originalSlot = -1;
		this.toolSlot = -1;
	}
}
