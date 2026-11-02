package dev.polarclient.gfx;

/**
 * Fonts the UI draws with. VANILLA is Minecraft's own font (HUD text, MOTDs); the rest are Polar's smooth TTF fonts
 * (Poppins for text, Material Symbols for icons) shipped in assets/polarclient/font.
 */
public enum UiFont {
	VANILLA(0, 0, 7f),
	SMALL(6.5f, 0, 4.5f),
	BODY(8f, 0, 5.6f),
	BOLD(8f, 0, 5.6f),
	LARGE(11f, 0, 7.7f),
	TITLE(16f, 0, 11.2f),
	ICON_S(9f, 9, 0),
	ICON_M(12f, 12, 0),
	ICON_M_FILL(12f, 12, 0),
	ICON_L(26f, 26, 0);

	/** Point size in GUI pixels (as in the font JSONs). */
	public final float size;
	/** For icon fonts: the square box an icon fills; 0 for text fonts. */
	public final int iconBox;
	/** Height of capital letters: text drawn at y has its caps from y to y + cap. */
	public final float cap;

	UiFont(float size, int iconBox, float cap) {
		this.size = size;
		this.iconBox = iconBox;
		this.cap = cap;
	}

	public boolean isIcon() {
		return iconBox > 0;
	}

	/** Resource id (polarclient:<id>) of the font definition. */
	public String id() {
		switch (this) {
			case SMALL: return "ui_small";
			case BODY: return "ui";
			case BOLD: return "ui_bold";
			case LARGE: return "ui_large";
			case TITLE: return "ui_title";
			case ICON_S: return "icon_s";
			case ICON_M: return "icon_m";
			case ICON_M_FILL: return "icon_m_fill";
			case ICON_L: return "icon_l";
			default: return "default";
		}
	}
}
