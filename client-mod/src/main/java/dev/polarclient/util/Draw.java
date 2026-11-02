package dev.polarclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Small drawing helpers on top of GuiGraphicsExtractor (rounded boxes, colour maths, text). */
public final class Draw {
	private Draw() {
	}

	// Polar palette
	public static final int NAVY = 0xFF0B1426;
	public static final int NAVY_2 = 0xFF111E38;
	public static final int NAVY_3 = 0xFF1A2B4C;
	public static final int ICE = 0xFFCFE5FF;
	public static final int ICE_BLUE = 0xFF8CC8FF;
	public static final int ACCENT = 0xFF4FA3FF;
	public static final int AURORA = 0xFF5CF2D6;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int MUTED = 0xFF8A9BBA;
	public static final int DANGER = 0xFFFF6B6B;

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	public static int alpha(int argb, int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
	}

	public static int alpha(int argb, float alpha) {
		int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, alpha)));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	public static int lerp(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
			| ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
	}

	/** Filled box with stepped corners of radius r (in GUI pixels). */
	public static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
		if (w <= 0 || h <= 0 || ((color >>> 24) & 0xFF) == 0) return;
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		if (r == 0) {
			g.fill(x, y, x + w, y + h, color);
			return;
		}
		g.fill(x, y + r, x + w, y + h - r, color);
		for (int i = 0; i < r; i++) {
			// inset for this row of the corner, from the circle equation
			double dy = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
			g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
			g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
		}
	}

	/** One-pixel outline following the same stepped corners as {@link #round}. */
	public static void roundOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
		if (w <= 0 || h <= 0) return;
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		if (r == 0) {
			g.outline(x, y, w, h, color);
			return;
		}
		for (int row = 0; row < h; row++) {
			int in = inset(row, h, r);
			if (row == 0 || row == h - 1) {
				g.fill(x + in, y + row, x + w - in, y + row + 1, color);
				continue;
			}
			// extend each side far enough to meet the rows above and below (closes stair gaps)
			int reach = Math.max(in, Math.max(inset(row - 1, h, r), inset(row + 1, h, r)) - 1);
			g.fill(x + in, y + row, x + reach + 1, y + row + 1, color);
			g.fill(x + w - reach - 1, y + row, x + w - in, y + row + 1, color);
		}
	}

	/** Horizontal inset of a rounded box's edge on a given row. */
	private static int inset(int row, int h, int r) {
		int i = row < r ? row : row >= h - r ? h - 1 - row : -1;
		if (i < 0) return 0;
		double dy = r - i - 0.5;
		return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
	}

	public static final Identifier MARK = Identifier.fromNamespaceAndPath("polarclient", "textures/gui/mark.png");
	public static final Identifier MARK_LARGE = Identifier.fromNamespaceAndPath("polarclient", "textures/gui/mark_large.png");
	public static final Identifier WORDMARK = Identifier.fromNamespaceAndPath("polarclient", "textures/gui/wordmark.png");
	/** Wordmark texture aspect ratio (width / height). */
	public static final float WORDMARK_ASPECT = 1196f / 384f;

	/** Draws a whole texture stretched into the box. */
	public static void texture(GuiGraphicsExtractor g, Identifier texture, int x, int y, int w, int h) {
		g.blit(texture, x, y, x + w, y + h, 0f, 1f, 0f, 1f);
	}

	public static void text(GuiGraphicsExtractor g, String s, int x, int y, int color, boolean shadow) {
		g.text(font(), s, x, y, color, shadow);
	}

	public static void centered(GuiGraphicsExtractor g, String s, int cx, int y, int color, boolean shadow) {
		g.text(font(), s, cx - font().width(s) / 2, y, color, shadow);
	}

	/** Cuts text to fit a width, adding "..." when it was too long. */
	public static String ellipsize(String s, int maxWidth) {
		Font f = font();
		if (f.width(s) <= maxWidth) return s;
		String dots = "...";
		return f.plainSubstrByWidth(s, Math.max(0, maxWidth - f.width(dots))) + dots;
	}

	public static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** Frame-rate independent approach of {@code current} towards {@code target}. */
	public static float approach(float current, float target, float speed, float deltaSeconds) {
		float k = 1f - (float) Math.exp(-speed * deltaSeconds);
		return current + (target - current) * k;
	}
}
