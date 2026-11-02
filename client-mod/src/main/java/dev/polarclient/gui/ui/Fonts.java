package dev.polarclient.gui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** Smooth TTF fonts shipped in assets/polarclient/font (Poppins for text, Material Symbols for icons). */
public final class Fonts {
	private Fonts() {
	}

	public static final FontDescription SMALL = res("ui_small");
	public static final FontDescription BODY = res("ui");
	public static final FontDescription BOLD = res("ui_bold");
	public static final FontDescription LARGE = res("ui_large");
	public static final FontDescription TITLE = res("ui_title");

	public static final FontDescription ICON_S = res("icon_s");
	public static final FontDescription ICON_M = res("icon_m");
	public static final FontDescription ICON_M_FILL = res("icon_m_fill");
	public static final FontDescription ICON_L = res("icon_l");

	private static FontDescription res(String id) {
		return new FontDescription.Resource(Identifier.fromNamespaceAndPath("polarclient", id));
	}

	public static Component text(String s, FontDescription font) {
		return Component.literal(s).withStyle(style -> style.withFont(font));
	}

	public static int width(String s, FontDescription font) {
		return Minecraft.getInstance().font.width(text(s, font));
	}

	/** Unrounded advance width, for letter-spaced text. */
	public static float exactWidth(String s, FontDescription font) {
		return Minecraft.getInstance().font.getSplitter().stringWidth(text(s, font));
	}

	/** Height of capital letters; text drawn at y has its caps from y to y + cap. */
	public static float cap(FontDescription font) {
		if (font == SMALL) return 4.5f;
		if (font == LARGE) return 7.7f;
		if (font == TITLE) return 11.2f;
		return 5.6f;
	}

	/** Size of the square icon box for an icon font. */
	public static int iconSize(FontDescription font) {
		if (font == ICON_S) return 9;
		if (font == ICON_L) return 26;
		return 12;
	}

	public static String ellipsize(String s, FontDescription font, int maxWidth) {
		if (width(s, font) <= maxWidth) return s;
		String dots = "...";
		int end = s.length();
		while (end > 0 && width(s.substring(0, end) + dots, font) > maxWidth) end--;
		return s.substring(0, end) + dots;
	}
}
