package com.sakura.client.module.impl;

import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import com.sakura.client.safety.SafetyManager;
import com.sakura.client.setting.BooleanSetting;
import com.sakura.client.setting.ChanceSetting;
import com.sakura.client.setting.EnumSetting;
import com.sakura.client.setting.NumberSetting;
import com.sakura.client.setting.Tagged;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Keeps a totem of undying in the offhand.
 *
 * <p>Port of the totem part of LiquidBounce's {@code ModuleOffhand} (GPL-3.0), which is the module its alias
 * list still calls {@code AutoTotem}. Only the totem is handled here: LiquidBounce's gapple, crystal, strength
 * and block modes are not ported, and neither is its damage prediction, so a low-health rule decides when the
 * totem is wanted instead of a simulated explosion.</p>
 *
 * <h2>How the item gets into the offhand</h2>
 * <p>Both modes are ordinary slot clicks sent through vanilla's own
 * {@code ClientPlayerInteractionManager.clickSlot}, so the server validates them exactly like a click made by
 * hand and nothing about the inventory is edited locally:</p>
 * <ul>
 *     <li><b>Switch</b> (default) —one {@code SWAP} click on the totem slot with button
 *     {@link PlayerInventory#OFF_HAND_SLOT}, which is the very action vanilla performs when the swap-hands key
 *     is pressed over a focused slot in an open inventory.</li>
 *     <li><b>PickUp</b> —up to three {@code PICKUP} clicks: take the totem to the cursor, click it into the
 *     offhand, and drop the item that was there back into the slot the totem came from. This is LiquidBounce's
 *     universal fallback and works where a single swap click is not accepted.</li>
 * </ul>
 *
 * <p>Nothing here hides anything from the server, so both modes are rated safe: an anticheat cannot tell these
 * clicks from a player's own, and the only thing it could profile is how promptly the module reacts. Clicks are
 * refused while any screen is open, so the module can never fight the player for the cursor.</p>
 */
public final class AutoTotemModule extends Module {

	/** The four armor slots; a gap in any of them is treated as being in danger. */
	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.FEET,
			EquipmentSlot.LEGS,
			EquipmentSlot.CHEST,
			EquipmentSlot.HEAD
	};

	private final BooleanSetting health = setting(new BooleanSetting("Health",
			"Only hold a totem while your health is low. Turn this off to hold one at all times.", true));
	private final NumberSetting healthThreshold = setting(new NumberSetting("Health Threshold",
			"Health, absorption included, at or below which a totem is wanted.", 14.0, 0.0, 20.0, 1.0, "hp"));
	private final BooleanSetting missingArmor = setting(new BooleanSetting("Missing Armor",
			"Treat a missing armor piece as being below the threshold.", true));
	private final EnumSetting<SwitchMode> switchMode = setting(new EnumSetting<>("SwitchMode",
			"How the totem is moved into the offhand.", SwitchMode.SWITCH));
	private final NumberSetting switchDelay = setting(new NumberSetting("Switch Delay",
			"Shortest time between two switches.", 0.0, 0.0, 500.0, 10.0, "ms"));
	private final BooleanSetting switchBack = setting(new BooleanSetting("Switch Back",
			"Put the item that was in the offhand back once the totem is no longer needed.", true));
	private final NumberSetting switchBackDelay = setting(new NumberSetting("Switch Back Delay",
			"How long the offhand has to stay unneeded before the item comes back.", 40.0, 0.0, 500.0, 10.0,
			"ms"));
	private final ChanceSetting chance = setting(new ChanceSetting("Chance",
			"Chance of reacting on any tick a totem is wanted. Below 100% the swap sometimes waits a few "
					+ "more ticks, so the reaction stops being instant every single time.", 60.0));

	/** The item the totem displaced, so it can be put back later. */
	private ItemStack displaced = ItemStack.EMPTY;
	private int displacedSlot = -1;
	private long lastSwitchAt = -1L;
	private long unneededSince = -1L;

	public AutoTotemModule() {
		super("AutoTotem", Category.COMBAT, "Keeps a totem of undying in your offhand.");

		this.healthThreshold.visibleWhen(() -> this.health.get());
		this.missingArmor.visibleWhen(() -> this.health.get());
		this.switchBackDelay.visibleWhen(() -> this.switchBack.get());
	}

	@Override
	public void onDisable() {
		forget();
	}

	@Override
	public void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();

		// A click belongs to whichever container the server thinks is open. With a screen open that is not the
		// player inventory, so the module stays out of the way and never steals the cursor from the player.
		if (!isEnabled() || client.player == null || client.world == null
				|| client.interactionManager == null || client.currentScreen != null) {
			return;
		}

		ClientPlayerEntity player = client.player;

		if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
			return;
		}

		PlayerInventory inventory = player.getInventory();

		if (wantsTotem(player)) {
			this.unneededSince = -1L;
			tickEquip(client, player, inventory);
		} else {
			tickSwitchBack(client, player, inventory);
		}
	}

	// -----------------------------------------------------------------------------------------------------------
	// Direction one: get a totem into the offhand
	// -----------------------------------------------------------------------------------------------------------

	private void tickEquip(MinecraftClient client, ClientPlayerEntity player, PlayerInventory inventory) {
		if (isTotem(player.getOffHandStack())) {
			return;
		}

		int source = findTotem(inventory);

		if (source < 0) {
			// No totem anywhere: leave whatever is in the offhand alone, it is better than an empty hand.
			return;
		}

		if (!delayElapsed()) {
			return;
		}

		// Rolled per opportunity rather than once per episode, mirroring how Velocity gates its jump: the
		// swap converges within a few ticks instead of always landing the instant the rule matches.
		if (!this.chance.roll()) {
			return;
		}

		if (!SafetyManager.canAct(getName())) {
			// The per-second allowance is spent; try again on the next tick. A slot click is the most visible
			// thing this module does, so it is exactly the kind of action the budget exists to stretch out.
			return;
		}

		// Remember what the swap pushes out, but only when it can be put back later.
		ItemStack offhand = player.getOffHandStack();

		if (this.switchBack.get() && !offhand.isEmpty()) {
			this.displaced = offhand.copy();
			this.displacedSlot = source;
		} else {
			forget();
		}

		SafetyManager.recordAction(getName());
		performSwap(client, player, inventory, source);
	}

	// -----------------------------------------------------------------------------------------------------------
	// Direction two: put the displaced item back
	// -----------------------------------------------------------------------------------------------------------

	private void tickSwitchBack(MinecraftClient client, ClientPlayerEntity player, PlayerInventory inventory) {
		if (!this.switchBack.get() || this.displacedSlot < 0 || this.displaced.isEmpty()) {
			this.unneededSince = -1L;
			return;
		}

		if (!isTotem(player.getOffHandStack())) {
			// The totem left the offhand by another route, so there is nothing of ours left to undo.
			forget();
			return;
		}

		if (!ItemStack.areItemsAndComponentsEqual(inventory.getStack(this.displacedSlot), this.displaced)) {
			// The slot no longer holds what it held when we swapped, so switching back would shuffle the wrong
			// item: the player reorganised the inventory and the memory is stale.
			forget();
			return;
		}

		long now = System.currentTimeMillis();

		if (this.unneededSince < 0L) {
			this.unneededSince = now;
			return;
		}

		if (now - this.unneededSince < this.switchBackDelay.intValue() || !delayElapsed(now)) {
			return;
		}

		if (!SafetyManager.canAct(getName())) {
			return;
		}

		SafetyManager.recordAction(getName());
		performSwap(client, player, inventory, this.displacedSlot);
		forget();
		this.unneededSince = -1L;
	}

	// -----------------------------------------------------------------------------------------------------------
	// Slot clicks
	// -----------------------------------------------------------------------------------------------------------

	/** Swaps the inventory slot at {@code sourceIndex} with the offhand slot, using the configured mode. */
	private void performSwap(MinecraftClient client, ClientPlayerEntity player, PlayerInventory inventory,
							 int sourceIndex) {
		PlayerScreenHandler handler = player.playerScreenHandler;

		if (handler == null) {
			return;
		}

		int sourceSlot = findScreenSlot(handler, inventory, sourceIndex);
		int offhandSlot = findScreenSlot(handler, inventory, PlayerInventory.OFF_HAND_SLOT);

		if (sourceSlot < 0 || offhandSlot < 0) {
			return;
		}

		// Read before any click: this decides whether the third pickup click is needed to park the item that
		// was in the offhand back into the slot the totem came from.
		boolean offhandOccupied = !player.getOffHandStack().isEmpty();

		switch (this.switchMode.get()) {
			case SWITCH -> click(client, player, handler, sourceSlot, PlayerInventory.OFF_HAND_SLOT,
					SlotActionType.SWAP);
			case PICKUP -> {
				click(client, player, handler, sourceSlot, 0, SlotActionType.PICKUP);
				click(client, player, handler, offhandSlot, 0, SlotActionType.PICKUP);

				if (offhandOccupied) {
					click(client, player, handler, sourceSlot, 0, SlotActionType.PICKUP);
				}
			}
		}

		this.lastSwitchAt = System.currentTimeMillis();
	}

	private static void click(MinecraftClient client, ClientPlayerEntity player, ScreenHandler handler, int slotId,
							  int button, SlotActionType action) {
		client.interactionManager.clickSlot(handler.syncId, slotId, button, action, player);
	}

	/**
	 * Resolves the id vanilla's screen handler uses for an inventory index.
	 *
	 * <p>The two numberings are not the same: the player screen handler holds the crafting grid and the armor
	 * slots in front of the inventory, so the hotbar lands at 36 and the offhand at 45. The mapping is read out of
	 * the handler instead of hard-coded, so it stays correct whatever order vanilla builds the slots in.</p>
	 *
	 * @return the screen handler slot id, or {@code -1} when no slot backs that inventory index
	 */
	private static int findScreenSlot(ScreenHandler handler, PlayerInventory inventory, int inventoryIndex) {
		for (int slotId = 0; slotId < handler.slots.size(); slotId++) {
			Slot slot = handler.slots.get(slotId);

			if (slot.inventory == inventory && slot.getIndex() == inventoryIndex) {
				return slotId;
			}
		}

		return -1;
	}

	// -----------------------------------------------------------------------------------------------------------
	// Decisions
	// -----------------------------------------------------------------------------------------------------------

	/**
	 * Whether a totem should be in the offhand right now.
	 *
	 * <p>Follows LiquidBounce's totem rule with the prediction stripped out: an empty armor slot counts as being
	 * in danger, otherwise the plain threshold decides. LiquidBounce additionally simulates the damage of nearby
	 * explosions, beds, respawn anchors and falls before answering; none of that is ported, so this is the
	 * conservative part of its rule and never swaps <em>less</em> eagerly than the threshold alone.</p>
	 */
	private boolean wantsTotem(PlayerEntity player) {
		if (!this.health.get()) {
			return true;
		}

		if (this.missingArmor.get() && hasMissingArmor(player)) {
			return true;
		}

		double allowedDamage = player.getHealth() + player.getAbsorptionAmount() - this.healthThreshold.get();
		return allowedDamage <= 0.0;
	}

	private static boolean hasMissingArmor(PlayerEntity player) {
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			if (player.getEquippedStack(slot).isEmpty()) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Whether a stack counts as a totem.
	 *
	 * <p>Matched on the death-protection component rather than on the item, which is what LiquidBounce does: the
	 * component is what protects the player, so anything carrying it belongs in the offhand.</p>
	 */
	private static boolean isTotem(ItemStack stack) {
		return !stack.isEmpty() && stack.contains(DataComponentTypes.DEATH_PROTECTION);
	}

	/** @return the inventory index of a totem, hotbar first like LiquidBounce, or {@code -1} when there is none */
	private static int findTotem(PlayerInventory inventory) {
		for (int index = 0; index < PlayerInventory.HOTBAR_SIZE; index++) {
			if (isTotem(inventory.getStack(index))) {
				return index;
			}
		}

		for (int index = PlayerInventory.HOTBAR_SIZE; index < PlayerInventory.MAIN_SIZE; index++) {
			if (isTotem(inventory.getStack(index))) {
				return index;
			}
		}

		return -1;
	}

	private boolean delayElapsed() {
		return delayElapsed(System.currentTimeMillis());
	}

	private boolean delayElapsed(long now) {
		return this.lastSwitchAt < 0L || now - this.lastSwitchAt >= this.switchDelay.intValue();
	}

	private void forget() {
		this.displaced = ItemStack.EMPTY;
		this.displacedSlot = -1;
	}

	/** How the totem is moved into the offhand. */
	public enum SwitchMode implements Tagged {

		/**
		 * One swap click on the totem slot with button 40, the offhand button. This is the exact click vanilla
		 * sends when the swap-hands key is pressed over a focused slot in an open inventory, and it is
		 * LiquidBounce's default for the same reason: one packet, and nothing to get out of step.
		 */
		SWITCH("Switch"),

		/**
		 * Pick the totem up, click it into the offhand and put the displaced item back: three plain pickup
		 * clicks. LiquidBounce keeps this as the fallback for servers that dislike swap clicks.
		 */
		PICKUP("PickUp");

		private final String label;

		SwitchMode(String label) {
			this.label = label;
		}

		@Override
		public String getTag() {
			return this.label;
		}
	}
}
