package dev.polarclient.impl;

import dev.polarclient.Polar;
import dev.polarclient.modules.HealthIndicatorModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;

/** Health Indicator on 1.8.9: the health text after name tags, and name tags over mobs when asked for. */
public final class HealthTags {
	private HealthTags() {
	}

	/**
	 * A name tag's text with the entity's health after it (players always, mobs when "Show on mobs" is on). Only the
	 * name line gets it: a player's scoreboard line is drawn the same way, and is left alone.
	 */
	public static String withHealth(String text, Entity entity) {
		HealthIndicatorModule m = module();
		if (m == null || text == null) return text;
		boolean player = entity instanceof PlayerEntity;
		if (!player && !(entity instanceof MobEntity && m.mobs.on())) return text;
		LivingEntity e = (LivingEntity) entity;
		if (e.getName() == null || !text.equals(e.getName().asFormattedString())) return text;
		String health = m.suffix(e.getHealth(), e.getMaxHealth(), e.getAbsorption());
		return health == null ? text : text + health;
	}

	/** Whether to show a mob's name tag (for its health) although the game wouldn't. */
	public static boolean forceMobTag(MobEntity mob) {
		HealthIndicatorModule m = module();
		Entity camera = MinecraftClient.getInstance().getCameraEntity();
		return m != null && camera != null && m.showsMob(mob.squaredDistanceTo(camera)) && !mob.isInvisible();
	}

	private static HealthIndicatorModule module() {
		HealthIndicatorModule m = Polar.modules() == null ? null : Polar.modules().healthIndicator;
		return m != null && m.isEnabled() ? m : null;
	}
}
