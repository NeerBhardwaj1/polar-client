package dev.polarclient.modules.hud;

import dev.polarclient.module.Category;
import dev.polarclient.module.TextHudModule;
import dev.polarclient.setting.BooleanSetting;

import java.util.List;

/** Which way you are facing, with the axis. */
public class DirectionModule extends TextHudModule {
	private final BooleanSetting showYaw = add(new BooleanSetting("yaw", "Show the exact angle", false));

	public DirectionModule() {
		super("direction", "Direction HUD", "Show which way you're facing.", Category.HUD, false, 4, 132);
	}

	@Override
	protected List<String> lines(boolean preview) {
		float yaw = preview && !p().inWorld() ? 180 : p().playerYaw();
		float y = ((yaw % 360) + 360) % 360;
		String[] names = {"South (+Z)", "South-West", "West (-X)", "North-West", "North (-Z)", "North-East", "East (+X)", "South-East"};
		String dir = names[Math.round(y / 45f) % 8];
		return one("Facing: " + dir + (showYaw.on() ? String.format(java.util.Locale.ROOT, " %.1f\u00b0", y) : ""));
	}
}
