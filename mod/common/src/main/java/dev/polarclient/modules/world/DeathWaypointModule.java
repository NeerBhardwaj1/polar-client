package dev.polarclient.modules.world;

import dev.polarclient.module.Category;
import dev.polarclient.module.TextHudModule;

import java.util.List;
import java.util.Locale;

/** Where you last died, and how far away it is. */
public class DeathWaypointModule extends TextHudModule {
	private double dx, dy, dz;
	private String dim;
	private boolean has, wasDead;

	public DeathWaypointModule() {
		super("death_waypoint", "Death Waypoints", "Remember where you last died.", Category.WORLD, true, 4, 220);
	}

	@Override
	public void tick() {
		if (!p().hasPlayer()) {
			wasDead = false;
			return;
		}
		boolean dead = p().health() <= 0;
		if (dead && !wasDead) {
			dx = p().playerX();
			dy = p().playerY();
			dz = p().playerZ();
			dim = p().dimension();
			has = true;
		}
		wasDead = dead;
	}

	/** The last death in this session, or null: {x, y, z}. */
	public double[] lastDeath() {
		return has ? new double[] {dx, dy, dz} : null;
	}

	public String lastDeathDimension() {
		return dim;
	}

	@Override
	public boolean hasContent() {
		return has && p().hasPlayer() && p().health() > 0 && dim != null && dim.equals(p().dimension());
	}

	@Override
	protected List<String> lines(boolean preview) {
		if (!has) return one("Last death: 120, 64, -48  (35 m)");
		double d = Math.sqrt(Math.pow(dx - p().playerX(), 2) + Math.pow(dz - p().playerZ(), 2));
		return one(String.format(Locale.ROOT, "Last death: %d, %d, %d  (%.0f m)", (int) Math.floor(dx), (int) Math.floor(dy), (int) Math.floor(dz), d));
	}
}
