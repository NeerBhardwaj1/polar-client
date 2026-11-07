package dev.polarclient.impl;

import dev.polarclient.Polar;
import dev.polarclient.modules.HealthIndicatorModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/** Health Indicator on 1.16.5: the health text after name tags, and name tags over mobs when asked for. */
public final class HealthTags {
	private HealthTags() {
	}

	/** A name tag with the entity's health after it (players always, mobs when "Show on mobs" is on). */
	public static Component withHealth(Component name, Entity entity) {
		HealthIndicatorModule m = module();
		if (m == null || name == null) return name;
		boolean player = entity instanceof Player;
		if (!player && !(entity instanceof Mob && m.mobs.on())) return name;
		LivingEntity e = (LivingEntity) entity;
		String health = m.suffix(e.getHealth(), e.getMaxHealth(), e.getAbsorptionAmount());
		return health == null ? name : new TextComponent("").append(name).append(new TextComponent(health));
	}

	/** Whether to show a mob's name tag (for its health) although the game wouldn't. */
	public static boolean forceMobTag(Mob mob) {
		HealthIndicatorModule m = module();
		Entity camera = Minecraft.getInstance().getCameraEntity();
		return m != null && camera != null && m.showsMob(mob.distanceToSqr(camera)) && !mob.isInvisible();
	}

	private static HealthIndicatorModule module() {
		HealthIndicatorModule m = Polar.modules() == null ? null : Polar.modules().healthIndicator;
		return m != null && m.isEnabled() ? m : null;
	}
}
