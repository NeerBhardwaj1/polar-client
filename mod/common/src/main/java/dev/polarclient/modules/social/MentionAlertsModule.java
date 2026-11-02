package dev.polarclient.modules.social;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.TextSetting;
import dev.polarclient.util.Popups;

import java.util.Locale;

/** Pings you when someone says your name in chat. */
public class MentionAlertsModule extends Module {
	private final TextSetting keywords = add(new TextSetting("keywords", "Extra words (comma separated)", "", 64));
	private final BooleanSetting sound = add(new BooleanSetting("sound", "Play a sound", true));
	private final BooleanSetting popup = add(new BooleanSetting("popup", "Show the message on screen", true));
	private long last;

	public MentionAlertsModule() {
		super("mention_alerts", "Mention Alerts", "Alert when someone says your name.", Category.SOCIAL, false);
	}

	@Override
	public void onChatMessage(String text) {
		if (!isEnabled()) return;
		String lower = text.toLowerCase(Locale.ROOT);
		String me = p().userName() == null ? "" : p().userName().toLowerCase(Locale.ROOT);
		// your own messages start with your name ("<Name> ..." or "Name: ...")
		if (!me.isEmpty() && (lower.startsWith("<" + me + ">") || lower.startsWith(me + ":") || lower.startsWith(me + " \u00bb"))) return;
		boolean hit = !me.isEmpty() && containsWord(lower, me);
		for (String k : keywords.get().split(",")) {
			String w = k.trim().toLowerCase(Locale.ROOT);
			if (!w.isEmpty() && containsWord(lower, w)) hit = true;
		}
		if (!hit || System.currentTimeMillis() - last < 1500) return;
		last = System.currentTimeMillis();
		if (sound.on()) p().playAlert(1, 1.4f);
		if (popup.on()) Popups.show(text, 0xFF5CC8FF, 3500);
	}

	private static boolean containsWord(String text, String word) {
		int i = text.indexOf(word);
		while (i >= 0) {
			boolean before = i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1));
			int end = i + word.length();
			boolean after = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
			if (before && after) return true;
			i = text.indexOf(word, i + 1);
		}
		return false;
	}
}
