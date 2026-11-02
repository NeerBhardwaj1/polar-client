package dev.polarclient.modules.chat;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.TextSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Hides chat lines with words you don't want to see, and everything from players you ignore. */
public class ChatFilterModule extends Module {
	public final TextSetting words = add(new TextSetting("words", "Hide words", "", 256));
	public final TextSetting players = add(new TextSetting("players", "Ignore players", "", 256));
	public final BooleanSetting joinLeave = add(new BooleanSetting("join_leave", "Hide join / leave messages", false));

	private String wordsSource, playersSource;
	private List<String> wordList = new ArrayList<String>(), playerList = new ArrayList<String>();

	public ChatFilterModule() {
		super("chat_filter", "Chat Filter", "Hides messages with words you list and players you ignore (comma-separated).", Category.SOCIAL, false);
	}

	private static List<String> split(String s) {
		List<String> out = new ArrayList<String>();
		for (String part : s.split("[,;]")) {
			String t = part.trim().toLowerCase(Locale.ROOT);
			if (!t.isEmpty()) out.add(t);
		}
		return out;
	}

	public boolean hides(String plain) {
		if (!words.get().equals(wordsSource)) {
			wordsSource = words.get();
			wordList = split(wordsSource);
		}
		if (!players.get().equals(playersSource)) {
			playersSource = players.get();
			playerList = split(playersSource);
		}
		String lower = plain.toLowerCase(Locale.ROOT);
		for (String w : wordList) if (lower.contains(w)) return true;
		if (joinLeave.on() && (lower.endsWith(" joined the game") || lower.endsWith(" left the game"))) return true;
		if (!playerList.isEmpty()) {
			String sender = senderPart(plain);
			if (sender != null) {
				for (String token : sender.split("[^A-Za-z0-9_]+")) {
					if (!token.isEmpty() && playerList.contains(token.toLowerCase(Locale.ROOT))) return true;
				}
			}
		}
		return false;
	}

	/** "<Steve> hi", "[VIP] Steve: hi", "Steve » hi": the part before the message, or null if it doesn't look like chat. */
	static String senderPart(String plain) {
		String s = plain.length() > 80 ? plain.substring(0, 80) : plain;
		if (s.startsWith("<")) {
			int end = s.indexOf('>');
			return end > 0 ? s.substring(1, end) : null;
		}
		int best = -1;
		for (String sep : new String[] {": ", " » ", " > ", " -> ", " whispers"}) {
			int i = s.indexOf(sep);
			if (i > 0 && (best < 0 || i < best)) best = i;
		}
		return best > 0 ? s.substring(0, best) : null;
	}
}
