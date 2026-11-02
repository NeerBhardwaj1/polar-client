package dev.polarclient.modules.hud;

import dev.polarclient.module.Category;
import dev.polarclient.module.TextHudModule;
import dev.polarclient.setting.ModeSetting;
import dev.polarclient.util.ClickTracker;

import java.util.List;

public class CpsModule extends TextHudModule {
	private final ModeSetting mode = add(new ModeSetting("mode", "Show", "Left + Right", "Left + Right", "Left only"));

	public CpsModule() {
		super("cps", "CPS", "Clicks per second for left and right click.", Category.COMBAT, true, 4, 42);
	}

	@Override
	protected List<String> lines(boolean preview) {
		int l = ClickTracker.leftCps(), r = ClickTracker.rightCps();
		return List.of(mode.is("Left only") ? l + " CPS" : l + " | " + r + " CPS");
	}
}
