package dev.polarclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** An ARGB colour picked from a palette, with an optional animated "chroma" (rainbow) mode. */
public class ColorSetting extends Setting<Integer> {
	/** Palette shown as swatches in the menu. */
	public static final int[] PALETTE = {
		0xFFFFFFFF, 0xFFCFE5FF, 0xFF8CC8FF, 0xFF4FA3FF, 0xFF5CF2D6, 0xFF6EE7A0,
		0xFFFFE066, 0xFFFFA94D, 0xFFFF6B6B, 0xFFF783AC, 0xFFB197FC, 0xFF9AA5B8,
		0xFF16305C, 0xFF000000
	};

	private boolean chroma;
	private final boolean allowAlpha;
	private int alpha;

	public ColorSetting(String id, String name, int defaultArgb, boolean allowAlpha) {
		super(id, name, defaultArgb);
		this.allowAlpha = allowAlpha;
		this.alpha = (defaultArgb >>> 24) & 0xFF;
	}

	public ColorSetting(String id, String name, int defaultArgb) {
		this(id, name, defaultArgb, false);
	}

	public boolean chroma() {
		return chroma;
	}

	public void setChroma(boolean chroma) {
		this.chroma = chroma;
	}

	public boolean allowsAlpha() {
		return allowAlpha;
	}

	public int alpha() {
		return alpha;
	}

	public void setAlpha(int alpha) {
		this.alpha = Math.max(0, Math.min(255, alpha));
	}

	/** Picks a palette colour, keeping the current transparency. */
	public void pick(int rgb) {
		set(rgb);
		chroma = false;
	}

	/** The colour to draw with right now; {@code offset} staggers chroma along a HUD element. */
	public int argb(float offset) {
		int rgb = chroma ? chromaRgb(offset) : (value & 0xFFFFFF);
		int a = allowAlpha ? alpha : 0xFF;
		return (a << 24) | rgb;
	}

	public int argb() {
		return argb(0);
	}

	public static int chromaRgb(float offset) {
		float hue = ((System.currentTimeMillis() % 4000L) / 4000f + offset) % 1f;
		return hsvToRgb(hue, 0.55f, 1f);
	}

	/** HSV (all 0..1) to packed 0xRRGGBB. */
	public static int hsvToRgb(float h, float s, float v) {
		float hh = (h - (float) Math.floor(h)) * 6f;
		int sector = (int) hh;
		float f = hh - sector;
		float p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
		float r, g, b;
		switch (sector) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
	}

	@Override
	public void reset() {
		super.reset();
		chroma = false;
		alpha = (defaultValue() >>> 24) & 0xFF;
	}

	@Override
	public JsonElement save() {
		JsonObject o = new JsonObject();
		o.addProperty("rgb", value & 0xFFFFFF);
		o.addProperty("alpha", alpha);
		o.addProperty("chroma", chroma);
		return o;
	}

	@Override
	public void load(JsonElement element) {
		if (element == null || !element.isJsonObject()) return;
		JsonObject o = element.getAsJsonObject();
		if (o.has("rgb")) set(0xFF000000 | o.get("rgb").getAsInt());
		if (o.has("alpha")) setAlpha(o.get("alpha").getAsInt());
		if (o.has("chroma")) chroma = o.get("chroma").getAsBoolean();
	}
}
