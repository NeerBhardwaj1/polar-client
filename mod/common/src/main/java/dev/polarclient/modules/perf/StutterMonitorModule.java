package dev.polarclient.modules.perf;

import dev.polarclient.module.Category;
import dev.polarclient.module.TextHudModule;
import dev.polarclient.setting.NumberSetting;
import dev.polarclient.util.FrameStats;

import java.util.List;
import java.util.Locale;

/** Counts frame-time spikes. */
public class StutterMonitorModule extends TextHudModule {
	private final NumberSetting window = add(new NumberSetting("window", "Count over", 60, 10, 300, 10, "s"));

	public StutterMonitorModule() {
		super("stutter_monitor", "Stutter Monitor", "Count lag spikes in frame time.", Category.PERFORMANCE, false, 380, 48);
	}

	@Override
	protected List<String> lines(boolean preview) {
		int n = FrameStats.spikes(window.getInt() * 1000L);
		return one("Stutters: " + n + " (" + window.getInt() + "s)  \u00b7  max " + String.format(Locale.ROOT, "%.0f ms", FrameStats.max()));
	}
}
