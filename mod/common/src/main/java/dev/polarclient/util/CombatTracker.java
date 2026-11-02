package dev.polarclient.util;

import dev.polarclient.platform.Platform;

/** Tracks your last hit's reach and your current combo, for the Reach Display and Combo Counter. */
public final class CombatTracker {
	private static double lastReach = -1;
	private static long lastHitTime;
	private static int combo;
	private static int lastHurtTime;

	private CombatTracker() {
	}

	/**
	 * You hit a living entity (each version calls this from its attack hook, for your own player only).
	 * {@code reach} = distance from your eyes to the target's hitbox.
	 */
	public static void onHit(double reach) {
		lastReach = reach;
		long now = System.currentTimeMillis();
		combo = now - lastHitTime < 2500 ? combo + 1 : 1;
		lastHitTime = now;
	}

	/** Every client tick: getting hit or waiting too long ends the combo. */
	public static void tick() {
		Platform p = Platform.get();
		if (!p.hasPlayer()) {
			combo = 0;
			return;
		}
		int hurt = p.hurtTime();
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
