package dev.polarclient.util;

import dev.polarclient.gfx.Gfx;

/** Short messages shown in the middle of the screen (kill popups, mention alerts), drawn with the HUD. */
public final class Popups {
	private static String text;
	private static int color;
	private static long shownAt;
	private static long duration;

	private Popups() {
	}

	public static void show(String message, int argb, long ms) {
		text = message;
		color = argb;
		shownAt = System.currentTimeMillis();
		duration = ms;
	}

	public static void render(Gfx g) {
		if (text == null) return;
		long age = System.currentTimeMillis() - shownAt;
		if (age > duration) {
			text = null;
			return;
		}
		float a = Math.min(1f, Math.min(age / 120f, (duration - age) / 400f));
		String s = Draw.ellipsize(text, g.width() - 40);
		int w = Draw.width(s) + 16, h = 15;
		int x = (g.width() - w) / 2, y = g.height() / 3 - 30;
		Draw.round(g, x, y, w, h, 4, Draw.alpha(0xCC0B1426, a));
		Draw.round(g, x, y + h - 1, w, 1, 0, Draw.alpha(color, a));
		g.text(s, x + 8, y + 4, Draw.alpha(0xFFFFFFFF, Math.max(0.05f, a)), true);
	}
}
