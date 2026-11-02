package dev.polarclient.gui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/**
 * Drawing toolkit for the Material-style screens. Shapes are drawn at real screen-pixel resolution with
 * anti-aliased corners (pre-rendered corner masks), so they stay smooth at any GUI scale.
 */
public final class Ui {
	private Ui() {
	}

	private static final int[] CORNER_SIZES = {8, 16, 32, 64, 128};
	private static final Identifier[] CORNERS = new Identifier[CORNER_SIZES.length];

	static {
		for (int i = 0; i < CORNER_SIZES.length; i++) {
			CORNERS[i] = Identifier.fromNamespaceAndPath("polarclient", "textures/gui/shape/corner_" + CORNER_SIZES[i] + ".png");
		}
	}

	private static long lastFrame = System.nanoTime();
	private static float dt = 0.016f;

	/** Call once per frame before animating. */
	public static void frame() {
		long now = System.nanoTime();
		dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
	}

	/** Exponential ease of {@code current} toward {@code target}; speed ~ 1/seconds. */
	public static float approach(float current, float target, float speed) {
		float v = current + (target - current) * (1f - (float) Math.exp(-speed * dt));
		return Math.abs(v - target) < 0.001f ? target : v;
	}

	public static float easeOut(float t) {
		t = Math.max(0, Math.min(1, t));
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}

	public static int scale() {
		return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
	}

	public static boolean inside(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// ------------------------------------------------------------------ shapes

	public static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int color) {
		rect(g, x, y, w, h, r, r, r, r, color);
	}

	/** Rounded rectangle with per-corner radii (top-left, top-right, bottom-right, bottom-left), in GUI units. */
	public static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h,
							float rtl, float rtr, float rbr, float rbl, int color) {
		if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
		int s = scale();
		int x0 = Math.round(x * s), y0 = Math.round(y * s), x1 = Math.round((x + w) * s), y1 = Math.round((y + h) * s);
		int width = x1 - x0, height = y1 - y0;
		if (width <= 0 || height <= 0) return;
		int max = Math.min(width, height) / 2;
		int a = clamp(Math.round(rtl * s), max), b = clamp(Math.round(rtr * s), max);
		int c = clamp(Math.round(rbr * s), max), d = clamp(Math.round(rbl * s), max);
		int left = Math.max(a, d), right = Math.max(b, c);

		g.pose().pushMatrix();
		g.pose().scale(1f / s);
		// centre column
		g.fill(x0 + left, y0, x1 - right, y1, color);
		// left column (between the two left corners) + the bits beside the smaller corner
		if (left > 0) {
			g.fill(x0, y0 + a, x0 + left, y1 - d, color);
			if (left > a) g.fill(x0 + a, y0, x0 + left, y0 + a, color);
			if (left > d) g.fill(x0 + d, y1 - d, x0 + left, y1, color);
		}
		if (right > 0) {
			g.fill(x1 - right, y0 + b, x1, y1 - c, color);
			if (right > b) g.fill(x1 - right, y0, x1 - b, y0 + b, color);
			if (right > c) g.fill(x1 - right, y1 - c, x1 - c, y1, color);
		}
		corner(g, x0, y0, a, false, false, color);
		corner(g, x1 - b, y0, b, true, false, color);
		corner(g, x1 - c, y1 - c, c, true, true, color);
		corner(g, x0, y1 - d, d, false, true, color);
		g.pose().popMatrix();
	}

	private static int clamp(int r, int max) {
		return Math.max(0, Math.min(r, max));
	}

	private static void corner(GuiGraphicsExtractor g, int x, int y, int r, boolean flipX, boolean flipY, int color) {
		if (r <= 0) return;
		int i = 0;
		while (i < CORNER_SIZES.length - 1 && CORNER_SIZES[i] < r) i++;
		int t = CORNER_SIZES[i];
		g.blit(RenderPipelines.GUI_TEXTURED, CORNERS[i], x, y, flipX ? t : 0, flipY ? t : 0, r, r,
			flipX ? -t : t, flipY ? -t : t, t, t, color);
	}

	public static void circle(GuiGraphicsExtractor g, float cx, float cy, float radius, int color) {
		rect(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
	}

	/**
	 * Filled rounded rect with a border {@code thickness} screen pixels wide.
	 * The fill is drawn over the border colour, so use an opaque fill for a clean ring.
	 */
	public static void outlined(GuiGraphicsExtractor g, float x, float y, float w, float h, float r,
								int fill, int border, float thicknessPx) {
		float t = thicknessPx / scale();
		rect(g, x, y, w, h, r, border);
		rect(g, x + t, y + t, w - 2 * t, h - 2 * t, Math.max(0, r - t), fill);
	}

	/** Straight line exactly {@code px} screen pixels thick. */
	public static void hline(GuiGraphicsExtractor g, float x, float y, float w, int px, int color) {
		int s = scale();
		g.pose().pushMatrix();
		g.pose().scale(1f / s);
		int y0 = Math.round(y * s);
		g.fill(Math.round(x * s), y0, Math.round((x + w) * s), y0 + px, color);
		g.pose().popMatrix();
	}

	public static void vline(GuiGraphicsExtractor g, float x, float y, float h, int px, int color) {
		int s = scale();
		g.pose().pushMatrix();
		g.pose().scale(1f / s);
		int x0 = Math.round(x * s);
		g.fill(x0, Math.round(y * s), x0 + px, Math.round((y + h) * s), color);
		g.pose().popMatrix();
	}

	/** One-screen-pixel rectangle outline (square corners), e.g. for HUD editor boxes. */
	public static void stroke(GuiGraphicsExtractor g, float x, float y, float w, float h, int color) {
		hline(g, x, y, w, 1, color);
		hline(g, x, y + h - 1f / scale(), w, 1, color);
		vline(g, x, y, h, 1, color);
		vline(g, x + w - 1f / scale(), y, h, 1, color);
	}

	/** Horizontal extent of a rounded rect (in screen px) on a given pixel row: {left, right}, or null if outside. */
	private static float[] span(float x0, float y0, float w, float h, float r, float rowCenter) {
		if (rowCenter < y0 || rowCenter > y0 + h) return null;
		float dy = 0;
		if (rowCenter < y0 + r) dy = y0 + r - rowCenter;
		else if (rowCenter > y0 + h - r) dy = rowCenter - (y0 + h - r);
		float inset = r - (float) Math.sqrt(Math.max(0, r * r - dy * dy));
		return new float[]{x0 + inset, x0 + w - inset};
	}

	/** Fills [l, r) on one pixel row, with partial-coverage alpha on the two edge pixels. */
	private static void aaSpan(GuiGraphicsExtractor g, float l, float r, int row, int color) {
		if (r <= l) return;
		int il = (int) Math.ceil(l), ir = (int) Math.floor(r);
		if (ir > il) g.fill(il, row, ir, row + 1, color);
		if (ir < il) { // narrower than one pixel
			g.fill(ir, row, il, row + 1, Theme.alpha(color, r - l));
			return;
		}
		float cl = il - l, cr = r - ir;
		if (cl > 0.02f) g.fill(il - 1, row, il, row + 1, Theme.alpha(color, cl));
		if (cr > 0.02f) g.fill(ir, row, ir + 1, row + 1, Theme.alpha(color, cr));
	}

	/** Rounded rect with a vertical gradient (top colour to bottom colour), anti-aliased sides. */
	public static void gradientRect(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int top, int bottom) {
		int s = scale();
		float x0 = x * s, y0 = y * s, ws = w * s, hs = h * s, rs = Math.min(r * s, Math.min(ws, hs) / 2);
		g.pose().pushMatrix();
		g.pose().scale(1f / s);
		for (int row = (int) Math.floor(y0); row < Math.ceil(y0 + hs); row++) {
			float[] sp = span(x0, y0, ws, hs, rs, row + 0.5f);
			if (sp == null) continue;
			aaSpan(g, sp[0], sp[1], row, Theme.lerp(top, bottom, (row + 0.5f - y0) / hs));
		}
		g.pose().popMatrix();
	}

	/** Outline only (nothing filled inside), {@code thicknessPx} screen pixels wide, anti-aliased. */
	public static void ring(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, int color, float thicknessPx) {
		int s = scale();
		float t = thicknessPx;
		float x0 = x * s, y0 = y * s, ws = w * s, hs = h * s, rs = Math.min(r * s, Math.min(ws, hs) / 2);
		float ix0 = x0 + t, iy0 = y0 + t, iw = ws - 2 * t, ih = hs - 2 * t, ir = Math.max(0, rs - t);
		g.pose().pushMatrix();
		g.pose().scale(1f / s);
		for (int row = (int) Math.floor(y0); row < Math.ceil(y0 + hs); row++) {
			float c = row + 0.5f;
			float[] o = span(x0, y0, ws, hs, rs, c);
			if (o == null) continue;
			float[] in = ih > 0 ? span(ix0, iy0, iw, ih, ir, c) : null;
			if (in == null) {
				aaSpan(g, o[0], o[1], row, color);
			} else {
				aaSpan(g, o[0], in[0], row, color);
				aaSpan(g, in[1], o[1], row, color);
			}
		}
		g.pose().popMatrix();
	}

	private static final Identifier GLOW = Identifier.fromNamespaceAndPath("polarclient", "textures/gui/menu/glow.png");

	/** Soft round glow centred on (cx, cy) with radii rx, ry (GUI units), tinted ARGB. */
	public static void glow(GuiGraphicsExtractor g, float cx, float cy, float rx, float ry, int argb) {
		if ((argb >>> 24) == 0 || rx <= 0 || ry <= 0) return;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(rx / 64f, ry / 64f);
		g.blit(RenderPipelines.GUI_TEXTURED, GLOW, -64, -64, 0, 0, 128, 128, 128, 128, 128, 128, argb);
		g.pose().popMatrix();
	}

	/** Letter-spaced text (for small caps labels), each letter placed on the nearest screen pixel. Returns the drawn width. */
	public static float textSpaced(GuiGraphicsExtractor g, String s, float x, float midY, int color, FontDescription font, float spacing) {
		int sc = scale();
		float cx = x;
		int y = Math.round(midY - Fonts.cap(font) / 2f);
		for (int i = 0; i < s.length(); i++) {
			String ch = String.valueOf(s.charAt(i));
			g.pose().pushMatrix();
			g.pose().translate(Math.round(cx * sc) / (float) sc, 0);
			g.text(Minecraft.getInstance().font, Fonts.text(ch, font), 0, y, color, false);
			g.pose().popMatrix();
			cx += Fonts.exactWidth(ch, font) + spacing;
		}
		return cx - x - spacing;
	}

	public static float spacedWidth(String s, FontDescription font, float spacing) {
		float w = 0;
		for (int i = 0; i < s.length(); i++) w += Fonts.exactWidth(String.valueOf(s.charAt(i)), font) + spacing;
		return Math.max(0, w - spacing);
	}

	/** Horizontal gradient (left colour to right colour), one screen-pixel column at a time so it never bands into stripes. */
	public static void hGradient(GuiGraphicsExtractor g, float x, float y, float w, float h, int left, int right) {
		int s = scale();
		int x0 = Math.round(x * s), x1 = Math.round((x + w) * s), y0 = Math.round(y * s), y1 = Math.round((y + h) * s);
		if (x1 <= x0 || y1 <= y0) return;
		g.pose().pushMatrix();
		g.pose().scale(1f / s);
		for (int col = x0; col < x1; col++) g.fill(col, y0, col + 1, y1, Theme.lerp(left, right, (col + 0.5f - x0) / (x1 - x0)));
		g.pose().popMatrix();
	}

	// ------------------------------------------------------------------ text & icons

	public static void text(GuiGraphicsExtractor g, String s, float x, float y, int color, FontDescription font) {
		g.text(Minecraft.getInstance().font, Fonts.text(s, font), Math.round(x), Math.round(y), color, false);
	}

	/** Text whose capitals are vertically centred on {@code midY}. */
	public static void textMid(GuiGraphicsExtractor g, String s, float x, float midY, int color, FontDescription font) {
		text(g, s, x, midY - Fonts.cap(font) / 2f, color, font);
	}

	public static void textCentered(GuiGraphicsExtractor g, String s, float cx, float midY, int color, FontDescription font) {
		textMid(g, s, cx - Fonts.width(s, font) / 2f, midY, color, font);
	}

	public static void textRight(GuiGraphicsExtractor g, String s, float right, float midY, int color, FontDescription font) {
		textMid(g, s, right - Fonts.width(s, font), midY, color, font);
	}

	/** Icon centred on (cx, cy). */
	public static void icon(GuiGraphicsExtractor g, String icon, float cx, float cy, int color, FontDescription font) {
		int size = Fonts.iconSize(font);
		g.text(Minecraft.getInstance().font, Fonts.text(icon, font), Math.round(cx - size / 2f), Math.round(cy - size / 2f), color, false);
	}
}
