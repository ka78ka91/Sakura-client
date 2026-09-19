package com.sakura.client.hud.element;

import com.sakura.client.hud.HudAnchor;
import com.sakura.client.hud.HudModule;
import com.sakura.client.render.RenderUtils;
import com.sakura.client.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Shows the player's armour pieces in a row, each with a durability bar once it is damaged.
 *
 * <p>Ported from LiquidBounce's armour HUD (GPL-3.0). In 1.21.11 armour is not part of the inventory any more
 * but lives in the entity's equipment, so the stacks are read from {@code getEquippedStack}.</p>
 *
 * <p>Slots are always laid out in the same order (helmet to boots) and empty pieces are simply not drawn, so
 * the panel does not jump around as armour is taken off.</p>
 */
public final class ArmorHudElement extends HudModule {

	/** Helmet first, boots last, the order a player reads them in. */
	private static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private static final float SLOT_SIZE = 18.0f;
	private static final float ICON_SIZE = 16.0f;
	private static final float PADDING = 3.0f;
	private static final float BAR_HEIGHT = 2.0f;
	private static final int BAR_BG = 0x80000000;

	private final BooleanSetting durabilityBar = setting(new BooleanSetting("Durability",
			"Draw a durability bar under every damaged piece.", true));
	private final BooleanSetting panel = setting(new BooleanSetting("Panel",
			"Draw a background panel behind the icons.", false));

	public ArmorHudElement() {
		super("ArmorHUD", "Armour pieces with durability", HudAnchor.BOTTOM_LEFT, 4.0f, 60.0f, false);
	}

	@Override
	public float getWidth() {
		return SLOTS.length * SLOT_SIZE + (this.panel.get() ? PADDING * 2.0f : 0.0f);
	}

	@Override
	public float getHeight() {
		return SLOT_SIZE + (this.panel.get() ? PADDING * 2.0f : 0.0f);
	}

	/**
	 * @return remaining durability as a fraction of the maximum, {@code 1.0} for anything that cannot be
	 * damaged (or has no maximum), clamped to 0..1
	 */
	public static float durabilityFraction(ItemStack stack) {
		if (stack.isEmpty() || !stack.isDamageable() || stack.getMaxDamage() <= 0) {
			return 1.0f;
		}

		return Math.min(1.0f, Math.max(0.0f, 1.0f - (float) stack.getDamage() / stack.getMaxDamage()));
	}

	/** @return durability colour, red when nearly broken and green while intact */
	public static int durabilityColor(float fraction) {
		int red = Math.round((1.0f - fraction) * 255.0f);
		int green = Math.round(fraction * 255.0f);

		return 0xFF000000 | (red << 16) | (green << 8);
	}

	@Override
	public void render(DrawContext context, float x, float y) {
		MinecraftClient client = MinecraftClient.getInstance();
		PlayerEntity player = client.player;

		if (player == null) {
			return;
		}

		if (this.panel.get()) {
			RenderUtils.drawRoundedRect(context, x, y, getWidth(), getHeight(), cornerRadius(), 0x66000000);
		}

		float originX = x + (this.panel.get() ? PADDING : 0.0f);
		float originY = y + (this.panel.get() ? PADDING : 0.0f);

		for (int index = 0; index < SLOTS.length; index++) {
			ItemStack stack = player.getEquippedStack(SLOTS[index]);

			if (stack.isEmpty()) {
				continue;
			}

			float slotX = originX + index * SLOT_SIZE;

			context.drawItem(stack, Math.round(slotX), Math.round(originY));

			if (!this.durabilityBar.get() || !stack.isDamageable() || stack.getMaxDamage() <= 0) {
				continue;
			}

			float fraction = durabilityFraction(stack);
			float barY = originY + ICON_SIZE + 1.0f;

			RenderUtils.drawRect(context, slotX, barY, ICON_SIZE, BAR_HEIGHT, BAR_BG);
			RenderUtils.drawRect(context, slotX, barY, ICON_SIZE * fraction, BAR_HEIGHT, durabilityColor(fraction));
		}
	}
}
