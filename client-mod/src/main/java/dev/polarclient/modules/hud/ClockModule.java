package dev.polarclient.modules.hud;

import dev.polarclient.module.TextHudModule;
import dev.polarclient.setting.BooleanSetting;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class ClockModule extends TextHudModule {
	private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
	private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT);
	private final BooleanSetting twelveHour = add(new BooleanSetting("twelve_hour", "12-hour clock", false));

	public ClockModule() {
		super("clock", "Clock", "Real-world time.", false, 430, 250);
	}

	@Override
	protected List<String> lines(boolean preview) {
		return List.of(LocalTime.now().format(twelveHour.on() ? H12 : H24));
	}
}
