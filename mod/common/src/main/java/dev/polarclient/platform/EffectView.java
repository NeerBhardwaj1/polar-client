package dev.polarclient.platform;

/** An active potion effect as the HUD sees it; {@code handle} lets the version draw its icon. */
public final class EffectView {
	public final Object handle;
	/** Localised name with level, e.g. "Speed II". */
	public final String name;
	/** e.g. "1:30" or "**:**". */
	public final String duration;
	/** Ends within 10 seconds (blinks). */
	public final boolean endingSoon;

	public EffectView(Object handle, String name, String duration, boolean endingSoon) {
		this.handle = handle;
		this.name = name;
		this.duration = duration;
		this.endingSoon = endingSoon;
	}

	public static String roman(int level) {
		switch (level) {
			case 2: return "II";
			case 3: return "III";
			case 4: return "IV";
			case 5: return "V";
			default: return String.valueOf(level);
		}
	}

	/** "Speed" + amplifier 1 -> "Speed II". */
	public static String withLevel(String name, int amplifier) {
		int level = amplifier + 1;
		return level > 1 ? name + " " + roman(level) : name;
	}

	/** Ticks -> "m:ss" (like the vanilla inventory). */
	public static String formatTicks(int ticks) {
		int secs = Math.max(0, ticks / 20);
		return (secs / 60) + ":" + (secs % 60 < 10 ? "0" : "") + (secs % 60);
	}
}
