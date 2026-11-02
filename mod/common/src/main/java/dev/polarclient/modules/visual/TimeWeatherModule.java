package dev.polarclient.modules.visual;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.ModeSetting;
import dev.polarclient.setting.NumberSetting;

/** Your own time of day and weather. Only you see it; the server keeps its own. */
public class TimeWeatherModule extends Module {
	public final ModeSetting time = add(new ModeSetting("time", "Time", "Day", "Server", "Sunrise", "Day", "Noon", "Sunset", "Night", "Midnight", "Custom"));
	public final NumberSetting hour = add(new NumberSetting("hour", "Hour", 12, 0, 23.5, 0.5, "h"));
	public final ModeSetting weather = add(new ModeSetting("weather", "Weather", "Clear", "Server", "Clear", "Rain", "Thunder"));

	public TimeWeatherModule() {
		super("time_weather", "Time & Weather", "Pick the time of day and weather you see. Only on your screen.", Category.VISUAL, false);
		hour.visibleWhen(() -> time.is("Custom"));
	}

	/** Time of day in ticks (0 = 6:00) to show, or -1 for the server's. */
	public long dayTime() {
		if (!isEnabled()) return -1;
		switch (time.get()) {
			case "Sunrise": return 23200;
			case "Day": return 1000;
			case "Noon": return 6000;
			case "Sunset": return 12300;
			case "Night": return 14000;
			case "Midnight": return 18000;
			case "Custom": return Math.round((hour.get() * 1000 + 18000) % 24000);
			default: return -1;
		}
	}

	/** Rain strength to show (0..1), given the server's. */
	public float rain(float server) {
		if (!isEnabled()) return server;
		if (weather.is("Clear")) return 0;
		if (weather.is("Rain") || weather.is("Thunder")) return 1;
		return server;
	}

	public float thunder(float server) {
		if (!isEnabled()) return server;
		if (weather.is("Thunder")) return 1;
		if (weather.is("Clear") || weather.is("Rain")) return 0;
		return server;
	}
}
