package dev.polarclient.world;

/** A named spot in one world and dimension. */
public final class Waypoint {
	public String name;
	public int x, y, z;
	/** Dimension id, e.g. "minecraft:overworld" (see Platform#dimension). */
	public String dim;
	public int color;
	public boolean visible = true;

	public Waypoint(String name, int x, int y, int z, String dim, int color) {
		this.name = name;
		this.x = x;
		this.y = y;
		this.z = z;
		this.dim = dim;
		this.color = color;
	}

	public double distanceTo(double px, double py, double pz) {
		double dx = x + 0.5 - px, dy = y - py, dz = z + 0.5 - pz;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
