package com.sakura.client.module.impl;

import com.sakura.client.module.Category;
import com.sakura.client.module.Module;
import com.sakura.client.setting.BooleanSetting;
import com.sakura.client.setting.EnumSetting;
import com.sakura.client.setting.MultiChoiceSetting;
import com.sakura.client.setting.NumberSetting;
import com.sakura.client.setting.Tagged;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.EntityHitResult;

import java.util.List;
import java.util.Set;

/**
 * Switches to the best weapon in the hotbar when it is needed.
 *
 * <p>Port of LiquidBounce's {@code ModuleAutoWeapon} (GPL-3.0). The rules it uses are kept: while a mace smash
 * attack would land only a mace is considered, while the target is blocking with a shield only an axe is
 * considered, and otherwise the weapons from {@code Preferred} compete by how much damage per second they
 * would deal. The slot then goes back to what the player was holding after a configurable delay, unless the
 * player changes slots in the meantime.</p>
 *
 * <h2>How it differs from LiquidBounce</h2>
 * <ul>
 *     <li>LiquidBounce swaps the slot <em>silently</em>: the client keeps showing the old item while the server
 *     is told the new one. That is a spoof and is exactly what gets profiled, so it is not ported. This module
 *     changes the selected slot for real, which is the same thing pressing a hotbar key does, and it is rated
 *     safe for that reason.</li>
 *     <li>The attack is not re-issued a tick later: vanilla's own
 *     {@code ClientPlayerInteractionManager.attackEntity} sends the held-slot update before the attack packet
 *     (verified in the 1.21.11 bytecode), so setting the slot in the attack hook is enough for the server to
 *     register the attack with the new weapon.</li>
 *     <li>LiquidBounce ranks weapons through its inventory cleaner, which estimates enchantment value and
 *     reachability. Here the score is read from the item's own attribute modifiers, which already include the
 *     material and the sharpness modifier.</li>
 *     <li>A blocking target is detected with vanilla's {@code LivingEntity.isBlocking()} rather than
 *     LiquidBounce's "would this attack hit the shield" test, which also checks that the target faces us.</li>
 * </ul>
 */
public final class AutoWeaponModule extends Module {

	/** The registered module, so the attack hook can reach it without a lookup. */
	private static AutoWeaponModule instance;
	private final MultiChoiceSetting<WeaponType> preferred = setting(new MultiChoiceSetting<>("Preferred",
			"Which weapons count when no shield break or mace smash applies. Empty means any weapon.",
			List.of(WeaponType.SWORD), WeaponType.class));
	private final BooleanSetting autoShieldBreak = setting(new BooleanSetting("Auto Shield Break",
			"Only consider axes while the target is blocking, since an axe disables the shield.", true));
	private final BooleanSetting autoMace = setting(new BooleanSetting("Auto Mace",
			"Only consider maces while vanilla says a smash attack would land.", true));
	private final EnumSetting<Trigger> trigger = setting(new EnumSetting<>("Trigger",
			"When to look for a better weapon.", Trigger.ON_ATTACK));
	private final NumberSetting switchBack = setting(new NumberSetting("Switch Back",
			"Ticks before the slot the player had selected comes back.", 20.0, 1.0, 300.0, 1.0, "t"));

	/** Slot the player was holding when the module took over, or {@code -1} when it is not holding a switch. */
	private int originalSlot = -1;
	/** Slot the module switched to, which is what it restores from. */
	private int switchedSlot = -1;
	private int ticksSinceUse;

	public AutoWeaponModule() {
		super("AutoWeapon", Category.COMBAT, "Selects the best weapon in your hotbar.");
		instance = this;
	}

	@Override
	public void onDisable() {
		restore(MinecraftClient.getInstance());
	}

	@Override
	public void onTick() {
		MinecraftClient client = MinecraftClient.getInstance();

		if (!isEnabled() || client.player == null || client.world == null) {
			return;
		}

		if (this.switchedSlot >= 0) {
			this.ticksSinceUse++;
		}

		// With a screen open the crosshair is frozen, so looking at it would act on a stale target.
		if (this.trigger.get() == Trigger.ON_TARGET && client.currentScreen == null) {
			LivingEntity target = crosshairTarget(client);

			if (target != null) {
				selectBest(client, target);
			}
		}

		restoreIfIdle(client);
	}

	// -----------------------------------------------------------------------------------------------------------
	// Attack hook, called from the attack mixin before vanilla's click handler runs
	// -----------------------------------------------------------------------------------------------------------

	/** Switches the weapon for the attack that is about to happen. */
	public static void beforeAttack(MinecraftClient client) {
		AutoWeaponModule module = instance;

		if (module == null || !module.isEnabled() || module.trigger.get() != Trigger.ON_ATTACK) {
			return;
		}

		if (client.player == null || client.world == null || client.currentScreen != null) {
			return;
		}

		LivingEntity target = crosshairTarget(client);

		if (target != null) {
			module.selectBest(client, target);
		}
	}

	// -----------------------------------------------------------------------------------------------------------
	// Slot handling
	// -----------------------------------------------------------------------------------------------------------

	private void selectBest(MinecraftClient client, LivingEntity target) {
		ClientPlayerEntity player = client.player;

		if (player.isUsingItem()) {
			return;
		}

		PlayerInventory inventory = player.getInventory();
		int current = inventory.getSelectedSlot();
		int best = bestWeaponSlot(player, inventory, target);

		if (best < 0 || best == current) {
			// Nothing better to hold: keep the switch timer alive so the module does not undo its own switch
			// while the player is still fighting.
			this.ticksSinceUse = 0;
			return;
		}

		if (this.switchedSlot != current) {
			// Whatever is held now is the player's own choice, so this is where a switch has to return to.
			this.originalSlot = current;
		}

		this.switchedSlot = best;
		this.ticksSinceUse = 0;
		inventory.setSelectedSlot(best);
	}

	private void restoreIfIdle(MinecraftClient client) {
		if (this.switchedSlot < 0 || this.originalSlot < 0) {
			return;
		}

		if (client.player.getInventory().getSelectedSlot() != this.switchedSlot) {
			// The player picked another slot by hand; the memory is stale and the module must not fight them.
			forget();
			return;
		}

		if (this.ticksSinceUse < this.switchBack.intValue()) {
			return;
		}

		client.player.getInventory().setSelectedSlot(this.originalSlot);
		forget();
	}

	private void restore(MinecraftClient client) {
		if (this.switchedSlot >= 0 && this.originalSlot >= 0 && client.player != null
				&& client.player.getInventory().getSelectedSlot() == this.switchedSlot) {
			client.player.getInventory().setSelectedSlot(this.originalSlot);
		}

		forget();
	}

	private void forget() {
		this.originalSlot = -1;
		this.switchedSlot = -1;
		this.ticksSinceUse = 0;
	}

	// -----------------------------------------------------------------------------------------------------------
	// Choosing the weapon
	// -----------------------------------------------------------------------------------------------------------

	/** @return the hotbar slot of the weapon to hold, or {@code -1} when the hotbar holds nothing suitable */
	private int bestWeaponSlot(ClientPlayerEntity player, PlayerInventory inventory, LivingEntity target) {
		boolean shieldBreak = this.autoShieldBreak.get() && target.isBlocking();
		boolean smash = this.autoMace.get() && MaceItem.shouldDealAdditionalDamage(player);

		int best = -1;
		double bestScore = 0.0;

		for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
			ItemStack stack = inventory.getStack(slot);

			if (stack.isEmpty() || !isCandidate(stack, shieldBreak, smash)) {
				continue;
			}

			double score = attackScore(stack);

			if (best < 0 || score > bestScore) {
				best = slot;
				bestScore = score;
			}
		}

		return best;
	}

	private boolean isCandidate(ItemStack stack, boolean shieldBreak, boolean smash) {
		return isCandidate(this.preferred.get(), shieldBreak, smash, stack);
	}

	/**
	 * The rule behind the choice, kept pure so it can be exercised without a fight.
	 *
	 * <p>A mace smash cannot be blocked, so it is worth more than any other weapon while it is available; an
	 * axe stuns a target that is blocking with a shield; otherwise the configured preference decides.</p>
	 */
	public static boolean isCandidate(Set<WeaponType> preferred, boolean shieldBreak, boolean smash, ItemStack stack) {
		if (smash) {
			return WeaponType.MACE.matches(stack);
		}

		if (shieldBreak) {
			return WeaponType.AXE.matches(stack);
		}

		if (preferred.isEmpty()) {
			return WeaponType.ANY.matches(stack);
		}

		for (WeaponType type : preferred) {
			if (type.matches(stack)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Damage per second a weapon would deal, as far as a single hit is concerned.
	 *
	 * <p>The attack damage modifiers are compared as they are instead of adding the bare hand's damage: that
	 * constant is the same for every candidate, so it cannot change which weapon wins. The attack speed does
	 * need its real base, because the weapon modifiers for it are negative and the product would otherwise
	 * invert the ranking. The base is read from the attribute's own default rather than hard-coded.</p>
	 */
	public static double attackScore(ItemStack stack) {
		double damage = modifierSum(stack, EntityAttributes.ATTACK_DAMAGE);
		double speed = baseAttackSpeed() + modifierSum(stack, EntityAttributes.ATTACK_SPEED);
		return damage * speed;
	}

	/** The attack damage a stack's main-hand modifiers add, which is what makes one weapon better than another. */
	public static double attackDamage(ItemStack stack) {
		return modifierSum(stack, EntityAttributes.ATTACK_DAMAGE);
	}

	public static double baseAttackSpeed() {
		return EntityAttributes.ATTACK_SPEED.value().getDefaultValue();
	}

	private static double modifierSum(ItemStack stack, RegistryEntry<EntityAttribute> attribute) {
		AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);

		if (modifiers == null) {
			return 0.0;
		}

		double added = 0.0;
		double multipliedBase = 0.0;
		double multipliedTotal = 0.0;

		for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
			if (entry.attribute().value() != attribute.value()
					|| !entry.slot().matches(EquipmentSlot.MAINHAND)) {
				continue;
			}

			double value = entry.modifier().value();

			switch (entry.modifier().operation()) {
				case ADD_VALUE -> added += value;
				case ADD_MULTIPLIED_BASE -> multipliedBase += value;
				case ADD_MULTIPLIED_TOTAL -> multipliedTotal += value;
			}
		}

		return (added) * (1.0 + multipliedBase) * (1.0 + multipliedTotal);
	}

	private static LivingEntity crosshairTarget(MinecraftClient client) {
		if (!(client.crosshairTarget instanceof EntityHitResult hit)) {
			return null;
		}

		if (!(hit.getEntity() instanceof LivingEntity living) || living == client.player || !living.isAlive()) {
			return null;
		}

		return living;
	}

	/** When the module goes looking for a better weapon. */
	public enum Trigger implements Tagged {

		/** Just before an attack on an entity, so the hit lands with the weapon the player meant to use. */
		ON_ATTACK("OnAttack"),

		/**
		 * Every tick an entity is under the crosshair. LiquidBounce drives this from its kill aura's target;
		 * this client has no kill aura, so the crosshair is the stand-in and the weapon is already in hand
		 * before the click.
		 */
		ON_TARGET("OnTarget");

		private final String label;

		Trigger(String label) {
			this.label = label;
		}

		@Override
		public String getTag() {
			return this.label;
		}
	}

	/** Which items count as weapons. LiquidBounce's tool and enchantment entries are left out. */
	public enum WeaponType implements Tagged {

		/** Anything that hits harder than a bare hand. */
		ANY("Any"),

		SWORD("Sword"),
		AXE("Axe"),
		MACE("Mace"),
		SPEAR("Spear"),
		TRIDENT("Trident");

		private final String label;

		WeaponType(String label) {
			this.label = label;
		}

		public boolean matches(ItemStack stack) {
			return switch (this) {
				case ANY -> attackDamage(stack) > 0.0;
				case SWORD -> stack.isIn(ItemTags.SWORDS);
				case AXE -> stack.isIn(ItemTags.AXES);
				case MACE -> stack.getItem() instanceof MaceItem;
				case SPEAR -> stack.isIn(ItemTags.SPEARS);
				case TRIDENT -> stack.isOf(Items.TRIDENT);
			};
		}

		@Override
		public String getTag() {
			return this.label;
		}
	}
}
