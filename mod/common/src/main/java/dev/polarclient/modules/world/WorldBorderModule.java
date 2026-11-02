package dev.polarclient.modules.world;

import dev.polarclient.module.Category;
import dev.polarclient.module.TextHudModule;
import dev.polarclient.setting.NumberSetting;

import java.util.List;
import java.util.Locale;

/** How far the world border is. */
public class WorldBorderModule extends TextHudModule {
	private final NumberSetting showWithin = add(new NumberSetting("within", "Only show within", 0, 0, 5000, 50, " blocks"));

	public WorldBorderModule() {
		super("world_border", "World Border Display", "Distance to the world border.", Category.WORLD, false, 4, 244);
	}

	@Override
	public boolean hasContent() {
		double d = p().worldBorderDistance();
		return d >= 0 && (showWithin.getInt() == 0 || d <= showWithin.getInt());
	}

	@Override
	protected List<String> lines(boolean preview) {
		double d = p().worldBorderDistance();
		if (d < 0) d = 2340;
		return one(String.format(Locale.ROOT, "Border: %,.0f blocks", d));
	}
}
