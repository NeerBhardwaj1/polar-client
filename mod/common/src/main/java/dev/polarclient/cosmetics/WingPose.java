package dev.polarclient.cosmetics;

/**
 * What a player is doing this frame, as far as their wings care. Each version fills one in while drawing the player
 * and passes it to {@link Cosmetics#models(java.util.UUID, boolean, WingPose)}; the wings then beat harder when you
 * sprint, fold when you sneak, spread to glide when you fall, and so on (blended smoothly, see
 * {@link Cosmetics}).
 */
public final class WingPose {
	/** 0 standing still .. 1 walking or running. */
	public float moving;
	public boolean sprinting, sneaking, onGround = true, flying, gliding, swimming, riding, sleeping;
	/** Wearing an elytra: wings are hidden (the two would overlap). */
	public boolean elytra;
	/** The body's turn, in degrees (the game's yaw), for the wings to trail a little behind fast turns. */
	public float bodyYaw;
	/** Squared distance from the camera in blocks; far away wings hold still to save work. */
	public double distanceSq;

	/** Just walking or standing (for callers that know nothing else). */
	public static WingPose walking(float moving) {
		WingPose p = new WingPose();
		p.moving = moving;
		return p;
	}
}
