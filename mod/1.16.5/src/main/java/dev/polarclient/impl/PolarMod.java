package dev.polarclient.impl;

import dev.polarclient.Polar;
import dev.polarclient.util.CombatTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Minecraft 1.16.5 entrypoint: hands the shared core a {@link McPlatform} and wires it into the game's events.
 * The HUD, chat and vanilla-overlay hooks are mixins (see GuiMixin).
 */
public class PolarMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Polar.init(new McPlatform());
		Keys.register();

		ClientTickEvents.END_CLIENT_TICK.register(mc -> Polar.tick());
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Polar.saveConfig());
		// fires on both client and integrated server, so only count your own hits
		AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
			if (player == Minecraft.getInstance().player && target instanceof LivingEntity) {
				CombatTracker.onHit(distance(player.getEyePosition(1.0f), target));
			}
			return InteractionResult.PASS;
		});
	}

	/** From your eyes to the nearest point of the target's hitbox. */
	static double distance(Vec3 eye, Entity target) {
		AABB b = target.getBoundingBox();
		double dx = Math.max(0, Math.max(b.minX - eye.x, eye.x - b.maxX));
		double dy = Math.max(0, Math.max(b.minY - eye.y, eye.y - b.maxY));
		double dz = Math.max(0, Math.max(b.minZ - eye.z, eye.z - b.maxZ));
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
