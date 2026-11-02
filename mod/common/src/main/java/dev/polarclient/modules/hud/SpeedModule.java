package dev.polarclient.modules.hud;

import dev.polarclient.module.TextHudModule;

import java.util.List;
import java.util.Locale;

public class SpeedModule extends TextHudModule {
	public SpeedModule() {
		super("speed", "Speed", "Your movement speed.", false, 4, 230);
	}

	@Override
	protected List<String> lines(boolean preview) {
		if (!p().hasPlayer()) return one("4.32 m/s");
		return one(String.format(Locale.ROOT, "%.2f m/s", p().horizontalSpeed()));
	}
}
