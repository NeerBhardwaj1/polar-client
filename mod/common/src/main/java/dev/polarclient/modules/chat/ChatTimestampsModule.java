package dev.polarclient.modules.chat;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ColorSetting;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** The time each chat message arrived, in front of it. */
public class ChatTimestampsModule extends Module {
	public final BooleanSetting twelveHour = add(new BooleanSetting("twelve_hour", "12-hour clock", false));
	public final BooleanSetting seconds = add(new BooleanSetting("seconds", "Show seconds", false));
	public final ColorSetting color = add(new ColorSetting("color", "Colour", 0xFF8A9BBA));

	public ChatTimestampsModule() {
		super("chat_timestamps", "Chat Timestamps", "Shows the time each chat message arrived.", Category.SOCIAL, false);
	}

	/** "[14:05] " for now. */
	public String stamp() {
		String pattern = (twelveHour.on() ? "h:mm" : "HH:mm") + (seconds.on() ? ":ss" : "") + (twelveHour.on() ? " a" : "");
		return "[" + new SimpleDateFormat(pattern, Locale.ROOT).format(new Date()) + "] ";
	}
}
