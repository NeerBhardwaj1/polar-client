package dev.polarclient.gui.ui;

import dev.polarclient.Polar;
import dev.polarclient.modules.ClientSettingsModule;

/**
 * Material-You style dark palette (pure black surfaces tinted by the accent), derived from one accent colour.
 * The accent is picked in Polar Settings > Accent color.
 */
public final class Theme {
	private final int primary;
	private final int onPrimary;
	private final int primaryContainer;
	private final int onPrimaryContainer;
	private final int surface;
	private final int surfaceContainerLowest;
	private final int surfaceContainerLow;
	private final int surfaceContainer;
	private final int surfaceContainerHigh;
	private final int surfaceContainerHighest;
	private final int onSurface;
	private final int onSurfaceVariant;
	private final int outline;
	private final int outlineVariant;
	private final int hairline;
	private final int scrim;
	private final int error;

	public Theme(int primary, int onPrimary, int primaryContainer, int onPrimaryContainer, int surface, int surfaceContainerLowest, int surfaceContainerLow, int surfaceContainer, int surfaceContainerHigh, int surfaceContainerHighest, int onSurface, int onSurfaceVariant, int outline, int outlineVariant, int hairline, int scrim, int error) {
		this.primary = primary;
		this.onPrimary = onPrimary;
		this.primaryContainer = primaryContainer;
		this.onPrimaryContainer = onPrimaryContainer;
		this.surface = surface;
		this.surfaceContainerLowest = surfaceContainerLowest;
		this.surfaceContainerLow = surfaceContainerLow;
		this.surfaceContainer = surfaceContainer;
		this.surfaceContainerHigh = surfaceContainerHigh;
		this.surfaceContainerHighest = surfaceContainerHighest;
		this.onSurface = onSurface;
		this.onSurfaceVariant = onSurfaceVariant;
		this.outline = outline;
		this.outlineVariant = outlineVariant;
		this.hairline = hairline;
		this.scrim = scrim;
		this.error = error;
	}

	public int primary() {
		return primary;
	}

	public int onPrimary() {
		return onPrimary;
	}

	public int primaryContainer() {
		return primaryContainer;
	}

	public int onPrimaryContainer() {
		return onPrimaryContainer;
	}

	public int surface() {
		return surface;
	}

	public int surfaceContainerLowest() {
		return surfaceContainerLowest;
	}

	public int surfaceContainerLow() {
		return surfaceContainerLow;
	}

	public int surfaceContainer() {
		return surfaceContainer;
	}

	public int surfaceContainerHigh() {
		return surfaceContainerHigh;
	}

	public int surfaceContainerHighest() {
		return surfaceContainerHighest;
	}

	public int onSurface() {
		return onSurface;
	}

	public int onSurfaceVariant() {
		return onSurfaceVariant;
	}

	public int outline() {
		return outline;
	}

	public int outlineVariant() {
		return outlineVariant;
	}

	public int hairline() {
		return hairline;
	}

	public int scrim() {
		return scrim;
	}

	public int error() {
		return error;
	}

	public static final String[] ACCENT_NAMES = {"Ice", "Aurora", "Ember", "Violet", "Rose", "Lime", "Gold"};
	public static final int[] ACCENTS = {0xFF8CC8FF, 0xFF5CF2D6, 0xFFFF843F, 0xFFB197FC, 0xFFFF8FB1, 0xFFA6E35A, 0xFFFFC857};

	private static Theme cached;
	private static String cachedKey = "";

	public static Theme current() {
		ClientSettingsModule client = Polar.modules() == null ? null : Polar.modules().client;
		String accent = client == null ? "Ice" : client.accent.get();
		boolean amoled = client == null || client.amoled.on();
		String key = accent + amoled;
		if (cached == null || !key.equals(cachedKey)) {
			cached = of(accentColor(accent), amoled);
			cachedKey = key;
		}
		return cached;
	}

	public static int accentColor(String name) {
		for (int i = 0; i < ACCENT_NAMES.length; i++) if (ACCENT_NAMES[i].equals(name)) return ACCENTS[i];
		return ACCENTS[0];
	}

	public static Theme of(int accent, boolean amoled) {
		int base = amoled ? 0xFF000000 : 0xFF0B1020;
		return new Theme(
			accent,
			mix(0xFF000000, accent, 0.12f),
			mix(base, accent, 0.22f),
			mix(accent, 0xFFFFFFFF, 0.15f),
			mix(base, accent, 0.00f),
			mix(base, accent, 0.00f),
			mix(base, accent, 0.03f),
			mix(base, accent, 0.06f),
			mix(base, accent, 0.11f),
			mix(base, accent, 0.16f),
			0xFFFFFFFF,
			mix(accent, 0xFFFFFFFF, 0.35f),
			accent,
			mix(base, accent, 0.28f),
			0x33FFFFFF,
			0x99000000,
			0xFFFF5A5A
		);
	}

	public static int mix(int a, int b, float t) {
		int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		int r = Math.round(ar + (br - ar) * t), g = Math.round(ag + (bg - ag) * t), bl = Math.round(ab + (bb - ab) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	/** Blend two ARGB colours including alpha. */
	public static int lerp(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int aa = (a >>> 24) & 0xFF, ba = (b >>> 24) & 0xFF;
		int alpha = Math.round(aa + (ba - aa) * t);
		return (alpha << 24) | (mix(a, b, t) & 0xFFFFFF);
	}

	/** Same colour with alpha scaled by f (0..1). */
	public static int alpha(int argb, float f) {
		int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, f)));
		return (a << 24) | (argb & 0xFFFFFF);
	}
}
