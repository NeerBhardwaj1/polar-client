package dev.polarclient.modules.hud;

import dev.polarclient.module.Category;
import dev.polarclient.module.TextHudModule;

import java.util.List;
import java.util.Locale;

/** Food level and hidden saturation. */
public class SaturationModule extends TextHudModule {
	public SaturationModule() {
		super("saturation", "Saturation HUD", "Show hunger and saturation.", Category.HUD, false, 360, 200);
	}

	@Override
	protected List<String> lines(boolean preview) {
		boolean live = !preview || p().hasPlayer();
		int food = live ? p().foodLevel() : 18;
		float sat = live ? p().saturation() : 5.2f;
		return one(String.format(Locale.ROOT, "Food: %d  \u00b7  Saturation: %.1f", food, sat));
	}

	@Override
	public boolean hasContent() {
		return p().hasPlayer() && !p().creativeOrSpectator();
	}
}
