package dev.polarclient.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.polarclient.Log;
import dev.polarclient.gfx.UiFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Polar's TrueType fonts on 1.8.9, which can't load TTF fonts itself. Glyphs are rasterised with Java's font engine
 * into atlas textures and placed the way newer Minecraft versions place them (4x oversampling, 1 em = the font size,
 * baseline 7 units down plus the font's shift), so text measures and lines up exactly as on 26.3.
 */
final class TtfFont {
	private static final int OVERSAMPLE = 4, ATLAS = 512, PAD = 1;
	private static final Map<UiFont, TtfFont> FONTS = new EnumMap<UiFont, TtfFont>(UiFont.class);
	private static final Map<String, Font> FILES = new HashMap<String, Font>();
	private static int pageCounter;

	/** Font settings from the 26.3 font definitions (assets/polarclient/font/*.json there). */
	static TtfFont of(UiFont font) {
		TtfFont f = FONTS.get(font);
		if (f == null) {
			switch (font) {
				case SMALL: f = new TtfFont("poppins_regular", 6.5f, -2.47f); break;
				case BOLD: f = new TtfFont("poppins_bold", 8f, -1.424f); break;
				case LARGE: f = new TtfFont("poppins_bold", 11f, 0.667f); break;
				case TITLE: f = new TtfFont("poppins_bold", 16f, 4.152f); break;
				case ICON_S: f = new TtfFont("symbols", 9f, 2f); break;
				case ICON_M: f = new TtfFont("symbols", 12f, 5f); break;
				case ICON_M_FILL: f = new TtfFont("symbols_fill", 12f, 5f); break;
				case ICON_L: f = new TtfFont("symbols", 26f, 19f); break;
				default: f = new TtfFont("poppins_medium", 8f, -1.424f);
			}
			FONTS.put(font, f);
		}
		return f;
	}

	private static Font load(String file) {
		Font base = FILES.get(file);
		if (base == null) {
			try (InputStream in = TtfFont.class.getResourceAsStream("/assets/polarclient/font/" + file + ".ttf")) {
				base = Font.createFont(Font.TRUETYPE_FONT, in);
			} catch (Exception e) {
				Log.warn("Couldn't load font " + file + "; using the system font", e);
				base = new Font(Font.SANS_SERIF, Font.PLAIN, 1);
			}
			FILES.put(file, base);
		}
		return base;
	}

	private static final class Glyph {
		float advance;
		boolean empty = true;
		Page page;
		float left, top, width, height; // GUI units from the pen position and baseline
		float u0, v0, u1, v1;
	}

	private static final class Page {
		final NativeImageBackedTexture texture = new NativeImageBackedTexture(ATLAS, ATLAS);
		final Identifier id = new Identifier("polarclient", "dynamic/font_atlas_" + (pageCounter++));
		int x = 0, y = 0, rowHeight = 0;
		boolean dirty, registered;

		/** Finds room for a w x h bitmap; returns {x, y} or null when the page is full. */
		int[] place(int w, int h) {
			if (x + w > ATLAS) {
				x = 0;
				y += rowHeight;
				rowHeight = 0;
			}
			if (y + h > ATLAS || w > ATLAS) return null;
			int[] at = {x, y};
			x += w;
			rowHeight = Math.max(rowHeight, h);
			return at;
		}

		void bind() {
			if (!registered) {
				MinecraftClient.getInstance().getTextureManager().loadTexture(id, texture);
				registered = true;
				dirty = true;
			}
			if (dirty) {
				texture.upload();
				dirty = false;
				GlStateManager.bindTexture(texture.getGlId());
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
				GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			}
			GlStateManager.bindTexture(texture.getGlId());
		}
	}

	private final Font font;
	private final float shiftY;
	private final FontRenderContext frc = new FontRenderContext(null, true, true);
	private final Map<Character, Glyph> glyphs = new HashMap<Character, Glyph>();
	private final List<Page> pages = new ArrayList<Page>();

	private TtfFont(String file, float size, float shiftY) {
		this.font = load(file).deriveFont(size * OVERSAMPLE);
		this.shiftY = shiftY;
	}

	private Glyph glyph(char c) {
		Glyph g = glyphs.get(c);
		if (g != null) return g;
		g = new Glyph();
		glyphs.put(c, g);
		GlyphVector gv = font.createGlyphVector(frc, new char[] {c});
		// the game's font engine hints advances to whole oversampled pixels
		g.advance = Math.round(gv.getGlyphMetrics(0).getAdvanceX()) / (float) OVERSAMPLE;
		Rectangle r = gv.getPixelBounds(frc, 0, 0);
		if (r.isEmpty()) return g;
		int w = r.width + PAD * 2, h = r.height + PAD * 2;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = img.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g2.setColor(java.awt.Color.WHITE);
		g2.drawGlyphVector(gv, PAD - r.x, PAD - r.y);
		g2.dispose();

		Page page = pages.isEmpty() ? null : pages.get(pages.size() - 1);
		int[] at = page == null ? null : page.place(w, h);
		if (at == null) {
			page = new Page();
			pages.add(page);
			at = page.place(w, h);
			if (at == null) return g; // larger than a page; never happens at these sizes
		}
		int[] pixels = page.texture.getPixels();
		for (int yy = 0; yy < h; yy++) {
			for (int xx = 0; xx < w; xx++) {
				int alpha = img.getRGB(xx, yy) >>> 24;
				pixels[(at[1] + yy) * ATLAS + at[0] + xx] = alpha << 24 | 0xFFFFFF;
			}
		}
		page.dirty = true;
		g.empty = false;
		g.page = page;
		g.left = (r.x - PAD) / (float) OVERSAMPLE;
		g.top = (r.y - PAD) / (float) OVERSAMPLE;
		g.width = w / (float) OVERSAMPLE;
		g.height = h / (float) OVERSAMPLE;
		g.u0 = at[0] / (float) ATLAS;
		g.v0 = at[1] / (float) ATLAS;
		g.u1 = (at[0] + w) / (float) ATLAS;
		g.v1 = (at[1] + h) / (float) ATLAS;
		return g;
	}

	float width(String s) {
		float w = 0;
		for (int i = 0; i < s.length(); i++) w += glyph(s.charAt(i)).advance;
		return w;
	}

	/** Draws s with its top-left at (x, y) like the game's text renderer; returns the end x. */
	float draw(String s, float x, float y, int argb, boolean shadow) {
		if (shadow) {
			int a = argb >>> 24;
			int dark = (argb & 0xFCFCFC) >> 2;
			run(s, x + 1, y + 1, a << 24 | dark);
		}
		return run(s, x, y, argb);
	}

	private float run(String s, float x, float y, int argb) {
		float baseline = y + 7 + shiftY;
		int a = argb >>> 24, r = argb >> 16 & 255, gr = argb >> 8 & 255, b = argb & 255;
		// make sure every glyph is rasterised (and its page uploaded) before drawing
		for (int i = 0; i < s.length(); i++) glyph(s.charAt(i));
		GlStateManager.enableTexture();
		GlStateManager.enableBlend();
		GlStateManager.blendFuncSeparate(770, 771, 1, 0);
		GlStateManager.alphaFunc(516, 0.001f);
		GlStateManager.color(1, 1, 1, 1);
		Tessellator t = Tessellator.getInstance();
		BufferBuilder buf = t.getBuffer();
		for (Page page : pages) {
			boolean any = false;
			float pen = x;
			for (int i = 0; i < s.length(); i++) {
				Glyph g = glyph(s.charAt(i));
				if (!g.empty && g.page == page) {
					if (!any) {
						page.bind();
						buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
						any = true;
					}
					float x0 = pen + g.left, y0 = baseline + g.top, x1 = x0 + g.width, y1 = y0 + g.height;
					buf.vertex(x0, y1, 0).texture(g.u0, g.v1).color(r, gr, b, a).next();
					buf.vertex(x1, y1, 0).texture(g.u1, g.v1).color(r, gr, b, a).next();
					buf.vertex(x1, y0, 0).texture(g.u1, g.v0).color(r, gr, b, a).next();
					buf.vertex(x0, y0, 0).texture(g.u0, g.v0).color(r, gr, b, a).next();
				}
				pen += g.advance;
			}
			if (any) t.draw();
		}
		GlStateManager.alphaFunc(516, 0.1f);
		return x + width(s);
	}
}
