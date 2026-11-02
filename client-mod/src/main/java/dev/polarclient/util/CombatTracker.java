package dev.polarclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/** Tracks your last hit's reach and your current combo, for the Reach Display and Combo Counter. */
public final class CombatTracker {
	private static double lastReach = -1;
	private static long lastHitTime;
	private static int combo;
	private static int lastHurtTime;

	private CombatTracker() {
	}

	/** Fabric AttackEntityCallback — fires on both client and integrated server, so filter to our own player. */
	public static InteractionResult onAttack(Player player, Level level, InteractionHand hand, Entity target, EntityHitResult hitResult) {
		Minecraft mc = Minecraft.getInstance();
		if (player != mc.player || !(target instanceof LivingEntity)) return InteractionResult.PASS;
		lastReach = Math.sqrt(target.getBoundingBox().distanceToSqr(player.getEyePosition()));
		long now = System.currentTimeMillis();
		combo = now - lastHitTime < 2500 ? combo + 1 : 1;
		lastHitTime = now;
		return InteractionResult.PASS;
	}

	/** Call every client tick: getting hit or waiting too long ends the combo. */
	public static void tick(Minecraft mc) {
		if (mc.player == null) {
			combo = 0;
			return;
		}
		int hurt = mc.player.hurtTime;
		if (hurt > 0 && lastHurtTime == 0) combo = 0;
		lastHurtTime = hurt;
		if (combo > 0 && System.currentTimeMillis() - lastHitTime > 2500) combo = 0;
	}

	public static double lastReach() {
		return lastReach;
	}

	public static long msSinceLastHit() {
		return System.currentTimeMillis() - lastHitTime;
	}

	public static int combo() {
		return combo;
	}
}
