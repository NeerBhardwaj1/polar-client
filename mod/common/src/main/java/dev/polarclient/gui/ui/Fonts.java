package dev.polarclient.gui.ui;

import dev.polarclient.gfx.UiFont;
import dev.polarclient.platform.Platform;

/** Smooth TTF fonts shipped in assets/polarclient/font (Poppins for text, Material Symbols for icons). */
public final class Fonts {
	private Fonts() {
	}

	public static final UiFont SMALL = UiFont.SMALL;
	public static final UiFont BODY = UiFont.BODY;
	public static final UiFont BOLD = UiFont.BOLD;
	public static final UiFont LARGE = UiFont.LARGE;
	public static final UiFont TITLE = UiFont.TITLE;

	public static final UiFont ICON_S = UiFont.ICON_S;
	public static final UiFont ICON_M = UiFont.ICON_M;
	public static final UiFont ICON_M_FILL = UiFont.ICON_M_FILL;
	public static final UiFont ICON_L = UiFont.ICON_L;

	public static int width(String s, UiFont font) {
		return Platform.get().textWidth(s, font);
	}

	/** Unrounded advance width, for letter-spaced text. */
	public static float exactWidth(String s, UiFont font) {
		return Platform.get().exactTextWidth(s, font);
	}

	/** Height of capital letters; text drawn at y has its caps from y to y + cap. */
	public static float cap(UiFont font) {
		return font.cap;
	}

	/** Size of the square icon box for an icon font. */
	public static int iconSize(UiFont font) {
		return font.iconBox;
	}

	public static String ellipsize(String s, UiFont font, int maxWidth) {
		if (width(s, font) <= maxWidth) return s;
		String dots = "...";
		int end = s.length();
		while (end > 0 && width(s.substring(0, end) + dots, font) > maxWidth) end--;
		return s.substring(0, end) + dots;
	}
}
