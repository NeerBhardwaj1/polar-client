package dev.polarclient.hooks;

import dev.polarclient.Polar;
import dev.polarclient.module.ModuleManager;

/**
 * What the chat mods do to a new chat line. Each version calls {@link #incoming} from its "add chat message" hook and
 * then builds the line in its own text classes (time stamp, click-to-copy, repeat counter), or drops it.
 */
public final class ChatHooks {
	/** 1.8.9 has no "copy" click action: a suggest-command click whose value starts with this copies instead. */
	public static final String COPY_PREFIX = "\u0000polar-copy:";

	public static final class Line {
		public boolean hide;
		/** "[14:05] " or null. */
		public String timestamp;
		public int timestampColor;
		/** Text to copy when the line is clicked, or null. */
		public String copyText;
		/** Above 1: the line repeats the one before it, which should be removed; show " (xN)". */
		public int repeat = 1;
	}

	private static String last;
	private static int count;

	private ChatHooks() {
	}

	/** Null when no chat mod is on (show the line as it is). {@code plain} is the text without formatting. */
	public static Line incoming(String plain) {
		ModuleManager m = Polar.modules();
		if (m == null || plain == null) return null;
		boolean filter = m.chatFilter.isEnabled(), stamps = m.chatTimestamps.isEnabled(), copy = m.copyChat.isEnabled(), compact = m.compactChat.isEnabled();
		if (!filter && !stamps && !copy && !compact) {
			last = null;
			return null;
		}
		Line l = new Line();
		if (filter && m.chatFilter.hides(plain)) {
			l.hide = true;
			return l;
		}
		if (compact && !plain.trim().isEmpty() && plain.equals(last)) {
			l.repeat = ++count;
		} else {
			last = plain.trim().isEmpty() ? null : plain;
			count = 1;
		}
		if (stamps) {
			l.timestamp = m.chatTimestamps.stamp();
			l.timestampColor = m.chatTimestamps.color.argb() & 0xFFFFFF;
		}
		if (copy) l.copyText = plain;
		return l;
	}

	/** The previous line couldn't be replaced (chat was cleared): count again from 1. */
	public static void repeatLost() {
		count = 1;
	}

	public static boolean customBackground() {
		ModuleManager m = Polar.modules();
		return m != null && m.chatBackground.isEnabled();
	}

	/**
	 * The chat background colour to draw instead of the game's {@code vanillaArgb}, keeping its fade-out:
	 * {@code fullAlpha} is the alpha (0-255) the game's background has while a line is fully visible.
	 */
	public static int background(int vanillaArgb, float fullAlpha) {
		int c = Polar.modules().chatBackground.color.argb();
		float fade = fullAlpha > 0.5f ? Math.min(1f, (vanillaArgb >>> 24) / fullAlpha) : 1f;
		int a = Math.round((c >>> 24) * fade);
		return (a << 24) | (c & 0xFFFFFF);
	}
}
