package com.sakura.client.hud.element;


import com.sakura.client.render.Theme;
import com.sakura.client.config.ConfigManager;
import com.sakura.client.hud.HudAnchor;
import com.sakura.client.hud.HudModule;
import com.sakura.client.render.Animations;
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
 * the panel does not jump around as armour is taken off. The optional background is the shared glass material,
 * and it fades in when the setting is switched on.</p>
 *
 * <p>A durability bar eases toward the piece's real durability instead of snapping with it: losing durability
 * is animated quickly, because that is the change worth noticing, while the slower repair animations are what
 * a repair or a fresh piece looks like. Bars below a quarter also pulse, which is the cue to swap the piece
 * before it breaks.</p>
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
	private static final float SLOT_RECESS_RADIUS = 3.0f;
	/** Fast while the piece is being worn down, slower while it is being repaired. */
	private static final float DAMAGE_SPEED = 11.0f;
	private static final float REPAIR_SPEED = 3.5f;
	private static final float PANEL_FADE_SPEED = 8.0f;
	/** Below this the bar pulses, which reads as "replace me" without any text. */
	private static final float CRITICAL_FRACTION = 0.25f;
	private static final long CRITICAL_PULSE_MILLIS = 900L;

	private static final int DURABILITY_FINE = 0xFF5BE86B;
	private static final int DURABILITY_WARN = 0xFFFFC04D;
	private static final int DURABILITY_CRITICAL = 0xFFFF4D4D;

	/** Glass body: a dark, slightly cool gradient drawn over the blurred world. */
	private static final float GLASS_SHADOW_SPREAD = 4.0f;
	/** Recess behind each icon, only drawn while the glass panel is up. */
	private static final int SLOT_RECESS = 0x12FFFFFF;

	private final BooleanSetting durabilityBar = setting(new BooleanSetting("Durability",
			"Draw a durability bar under every damaged piece.", true));
	private final BooleanSetting panel = setting(new BooleanSetting("Panel",
			"Draw a background panel behind the icons.", false));

	private final Animations.Clock clock = new Animations.Clock();
	private final float[] shownDurability = new float[SLOTS.length];
	private final boolean[] durabilityPrimed = new boolean[SLOTS.length];
	private float panelFade;

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

	/**
	 * @return durability colour, red when nearly broken and green while intact. The ramp passes through amber
	 * rather than interpolating straight from red to green, which would go through a muddy brown.
	 */
	public static int durabilityColor(float fraction) {
		float clamped = Animations.clamp01(fraction);

		return clamped < 0.5f
				? RenderUtils.mix(DURABILITY_CRITICAL, DURABILITY_WARN, clamped * 2.0f)
				: RenderUtils.mix(DURABILITY_WARN, DURABILITY_FINE, (clamped - 0.5f) * 2.0f);
	}

	@Override
	public void render(DrawContext context, float x, float y) {
		MinecraftClient client = MinecraftClient.getInstance();
		PlayerEntity player = client.player;

		if (player == null) {
			return;
		}

		float delta = this.clock.tick();

		this.panelFade = Animations.approach(this.panelFade, this.panel.get() ? 1.0f : 0.0f,
				PANEL_FADE_SPEED, delta);

		float originX = x + (this.panel.get() ? PADDING : 0.0f);
		float originY = y + (this.panel.get() ? PADDING : 0.0f);

		if (this.panelFade > 0.01f) {
			drawGlass(context, x, y, getWidth(), getHeight(), cornerRadius(), this.panelFade);

			for (int index = 0; index < SLOTS.length; index++) {
				RenderUtils.drawRoundedRect(context, originX + index * SLOT_SIZE - 1.0f, originY - 1.0f,
						SLOT_SIZE, SLOT_SIZE, SLOT_RECESS_RADIUS,
						RenderUtils.multiplyAlpha(SLOT_RECESS, this.panelFade));
			}
		}

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

			float fraction = advanceDurability(index, durabilityFraction(stack), delta);
			float barY = originY + ICON_SIZE;

			RenderUtils.drawProgressBar(context, slotX, barY, ICON_SIZE, BAR_HEIGHT, fraction,
					BAR_BG, barColor(fraction));
		}
	}

	/** Eases one slot's bar toward the piece's real durability. */
	private float advanceDurability(int index, float target, float delta) {
		if (!this.durabilityPrimed[index]) {
			this.durabilityPrimed[index] = true;
			this.shownDurability[index] = target;
			return target;
		}

		float current = this.shownDurability[index];
		float speed = target < current ? DAMAGE_SPEED : REPAIR_SPEED;

		this.shownDurability[index] = Animations.approach(current, target, speed, delta);

		return this.shownDurability[index];
	}

	/** The eased durability colour, with the pulse that warns about a piece that is about to break. */
	private static int barColor(float fraction) {
		int color = durabilityColor(fraction);

		if (fraction >= CRITICAL_FRACTION) {
			return color;
		}

		return RenderUtils.brighten(color, Animations.breathe(CRITICAL_PULSE_MILLIS, 0.0f) * 0.35f);
	}

	/** The shared Sakura glass material: gradient body, hairline border, drop shadow and an accent wash. */
	private void drawGlass(DrawContext context, float x, float y, float width, float height,
						   float radius, float alpha) {
		RenderUtils.drawGlassPanel(context, x, y, width, height, radius,
				RenderUtils.multiplyAlpha(Theme.glassTop(), alpha), RenderUtils.multiplyAlpha(Theme.glassBottom(), alpha),
				RenderUtils.multiplyAlpha(Theme.glassBorder(), alpha), RenderUtils.multiplyAlpha(Theme.glassShadow(), alpha),
				GLASS_SHADOW_SPREAD);
		// Armor draws no text, so its base colour takes over the accent wash instead — the one visible
		// tint the player can claim for this element.
		RenderUtils.drawAccentWash(context, x, y, width, height, radius,
				themed(ConfigManager.get().accentColor), alpha);
	}
}
