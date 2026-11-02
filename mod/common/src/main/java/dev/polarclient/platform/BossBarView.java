package dev.polarclient.platform;

/** One boss bar at the top of the screen. */
public final class BossBarView {
	/** Pink, blue, red, green, yellow, purple, white (the game's boss bar colours, in its order). */
	public static final int[] COLORS = {0xFFEC00B8, 0xFF00B7EC, 0xFFEC3500, 0xFF1DEC00, 0xFFE9EC00, 0xFF7B00EC, 0xFFE0E0E0};

	public final Object name;
	public final float progress;
	public final int color;
	/** Segments drawn over the bar: 0, 6, 10, 12 or 20. */
	public final int notches;

	public BossBarView(Object name, float progress, int colorIndex, int notches) {
		this.name = name;
		this.progress = Math.max(0, Math.min(1, progress));
		this.color = COLORS[Math.max(0, Math.min(COLORS.length - 1, colorIndex))];
		this.notches = notches;
	}
}
