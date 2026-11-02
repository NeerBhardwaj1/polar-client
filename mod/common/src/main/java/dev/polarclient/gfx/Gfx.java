package dev.polarclient.gfx;

import dev.polarclient.platform.EffectView;
import dev.polarclient.platform.ItemView;

import java.util.UUID;

/**
 * Everything Polar's screens and HUD draw with, for one frame. Each Minecraft version implements it on top of that
 * version's own GUI renderer. Coordinates are GUI units in the current transform (push/translate/scale/pop).
 */
public interface Gfx {
	/** GUI-scaled screen size. */
	int width();

	int height();

	/** GUI scale factor (screen pixels per GUI unit). */
	int guiScale();

	void push();

	void pop();

	void translate(float x, float y);

	void scale(float sx, float sy);

	/** Rotates what's drawn next this many degrees clockwise around (0, 0). Set up scissors before rotating. */
	void rotate(float degrees);

	default void scale(float s) {
		scale(s, s);
	}

	/** Solid rectangle from (x0, y0) to (x1, y1). */
	void fill(int x0, int y0, int x1, int y1, int argb);

	/** Vertical gradient, top colour to bottom colour. */
	void gradient(int x0, int y0, int x1, int y1, int top, int bottom);

	/**
	 * Draws part of a texture (assets/polarclient/&lt;path&gt;) stretched into the box, tinted ARGB.
	 * UVs are 0..1; swapping u0/u1 or v0/v1 mirrors the image.
	 */
	void blit(String texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argb);

	default void blit(String texture, float x, float y, float w, float h) {
		blit(texture, x, y, w, h, 0, 0, 1, 1, 0xFFFFFFFF);
	}

	/** Draws a texture created at runtime (a server favicon from {@link dev.polarclient.platform.ServerBackend}). */
	void blitHandle(Object texture, float x, float y, float w, float h);

	void text(String s, float x, float y, int argb, UiFont font, boolean shadow);

	/** Text in Minecraft's own font (with shadow if asked). */
	default void text(String s, float x, float y, int argb, boolean shadow) {
		text(s, x, y, argb, UiFont.VANILLA, shadow);
	}

	/** A formatted game text (e.g. a server MOTD) in Minecraft's font, wrapped to maxWidth, at most maxLines lines. */
	void richText(Object component, float x, float y, int maxWidth, int maxLines, int lineHeight, int argb);

	/** One line of a formatted game text (a scoreboard line, a player name in Tab) in Minecraft's font. */
	void richLine(Object component, float x, float y, int argb, boolean shadow);

	/** Clips drawing to the rectangle (in the current transform) until {@link #endScissor()}; nests. */
	void scissor(int x0, int y0, int x1, int y1);

	void endScissor();

	/** Starts a new layer drawn above everything so far (dropdowns over the rest of the screen). */
	void nextLayer();

	void item(ItemView item, int x, int y);

	/** Durability bar etc. on top of an item. */
	void itemDecorations(ItemView item, int x, int y);

	void effectIcon(EffectView effect, int x, int y, int size, float alpha);

	/** A player's face (skin head with hat layer), size x size GUI units. */
	void playerHead(UUID id, String name, float x, float y, float size);

	/** Blurs whatever is behind the screen (menu blur); may do nothing on versions without it. */
	void blurBackground();
}
