package com.sakura.client.module.impl;

import com.sakura.client.module.Category;
import com.sakura.client.module.RenderModule;
import com.sakura.client.setting.BooleanSetting;
import com.sakura.client.setting.ColorSetting;
import com.sakura.client.setting.MultiChoiceSetting;
import com.sakura.client.setting.NumberSetting;
import com.sakura.client.setting.Tagged;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Target selection shared by the world overlay modules (ESP, NameTags, Tracers).
 *
 * <p>All three answer the same two questions — which entities are worth drawing, and what colour each of them
 * gets — so both live here instead of being copied three times. The settings are declared in this class, which
 * means every subclass gets the same "Targets / Range / colours" block in the settings panel before its own
 * parameters.</p>
 *
 * <p>Only living entities are considered. Entities such as dropped items or boats have no meaningful box to
 * draw for a player looking for other players and mobs, and LiquidBounce filters them out by type as well.</p>
 */
abstract class EntityOverlayModule extends RenderModule {

	/** The kinds of entity an overlay can be pointed at. */
	enum EntityGroup implements Tagged {

		PLAYERS("Players"),
		MOBS("Mobs"),
		ANIMALS("Animals"),
		OTHERS("Others");

		private final String tag;

		EntityGroup(String tag) {
			this.tag = tag;
		}

		@Override
		public String getTag() {
			return this.tag;
		}
	}

	/** Reused between frames and modules; the overlay is only ever built on the render thread. */
	private final List<Entity> selected = new ArrayList<>();

	protected final MultiChoiceSetting<EntityGroup> targets = setting(new MultiChoiceSetting<>("Targets",
			"Which kinds of entity to draw.", List.of(EntityGroup.PLAYERS, EntityGroup.MOBS), EntityGroup.class));
	protected final NumberSetting range = setting(new NumberSetting("Range",
			"Furthest distance an entity is still drawn at.", 48.0, 4.0, 256.0, 2.0, "m"));
	protected final BooleanSetting ignoreInvisible = setting(new BooleanSetting("Ignore Invisible",
			"Skip entities that are invisible to the client, e.g. spectators and invisible mobs.", true));
	protected final ColorSetting playerColor = setting(new ColorSetting("Player Color",
			"Colour used for other players.", 0xFFFF5555));
	protected final ColorSetting mobColor = setting(new ColorSetting("Mob Color",
			"Colour used for hostile mobs.", 0xFFFFAA00));
	protected final ColorSetting animalColor = setting(new ColorSetting("Animal Color",
			"Colour used for passive mobs.", 0xFF55FF55));
	protected final ColorSetting otherColor = setting(new ColorSetting("Other Color",
			"Colour used for living entities that are neither players, hostile nor passive, e.g. armour stands.",
			0xFFAAAAAA));

	protected EntityOverlayModule(String name, String description) {
		super(name, Category.VISUALS, description);
	}

	/**
	 * Collects the entities this module should draw right now, nearest first.
	 *
	 * <p>The returned list is reused between calls, so it has to be consumed before the next module renders.</p>
	 */
	protected final List<Entity> collectTargets(MinecraftClient client) {
		this.selected.clear();

		if (client.player == null || client.world == null) {
			return this.selected;
		}

		double limit = this.range.get();
		double limitSquared = limit * limit;

		for (Entity entity : client.world.getEntities()) {
			if (isTarget(client, entity, limitSquared)) {
				this.selected.add(entity);
			}
		}

		this.selected.sort((first, second) ->
				Double.compare(first.squaredDistanceTo(client.player), second.squaredDistanceTo(client.player)));

		return this.selected;
	}

	private boolean isTarget(MinecraftClient client, Entity entity, double limitSquared) {
		// The player is never a target, and neither is whatever the camera is riding: in third person that is
		// still the player, and boxing your own body is never what "other entities" means.
		if (entity == client.player || entity == client.getCameraEntity()) {
			return false;
		}

		if (entity.isRemoved() || !entity.isAlive() || !(entity instanceof LivingEntity)) {
			return false;
		}

		if (this.ignoreInvisible.get() && entity.isInvisible()) {
			return false;
		}

		if (entity.squaredDistanceTo(client.player) > limitSquared) {
			return false;
		}

		return this.targets.get().contains(groupOf(entity));
	}

	/** @return the colour configured for the group this entity belongs to */
	protected final int colorFor(Entity entity) {
		return switch (groupOf(entity)) {
			case PLAYERS -> this.playerColor.get();
			case MOBS -> this.mobColor.get();
			case ANIMALS -> this.animalColor.get();
			case OTHERS -> this.otherColor.get();
		};
	}

	protected static EntityGroup groupOf(Entity entity) {
		if (entity instanceof PlayerEntity) {
			return EntityGroup.PLAYERS;
		}

		if (entity instanceof Monster) {
			return EntityGroup.MOBS;
		}

		if (entity instanceof PassiveEntity) {
			return EntityGroup.ANIMALS;
		}

		return EntityGroup.OTHERS;
	}
}
