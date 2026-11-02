package dev.polarclient.modules.hud;

import dev.polarclient.module.TextHudModule;
import dev.polarclient.setting.BooleanSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CoordinatesModule extends TextHudModule {
	private final BooleanSetting showDirection = add(new BooleanSetting("direction", "Show facing", true));
	private final BooleanSetting showBiome = add(new BooleanSetting("biome", "Show biome", true));
	private final BooleanSetting decimals = add(new BooleanSetting("decimals", "Decimals", false));

	public CoordinatesModule() {
		super("coordinates", "Coordinates", "Show your XYZ position.", false, 4, 160);
	}

	@Override
	protected List<String> lines(boolean preview) {
		List<String> out = new ArrayList<String>();
		if (!p().hasPlayer()) {
			out.add("X: 128");
			out.add("Y: 64");
			out.add("Z: -256");
			if (showDirection.on()) out.add("Facing: North (-Z)");
			if (showBiome.on()) out.add("Biome: Plains");
			return out;
		}
		String fmt = decimals.on() ? "%.1f" : "%.0f";
		double x = p().playerX(), y = p().playerY(), z = p().playerZ();
		out.add("X: " + String.format(Locale.ROOT, fmt, decimals.on() ? x : Math.floor(x)));
		out.add("Y: " + String.format(Locale.ROOT, fmt, decimals.on() ? y : Math.floor(y)));
		out.add("Z: " + String.format(Locale.ROOT, fmt, decimals.on() ? z : Math.floor(z)));
		if (showDirection.on()) {
			String d = p().facing();
			String axis = "north".equals(d) ? " (-Z)" : "south".equals(d) ? " (+Z)" : "east".equals(d) ? " (+X)" : "west".equals(d) ? " (-X)" : "";
			out.add("Facing: " + capitalize(d) + axis);
		}
		if (showBiome.on()) {
			String biome = p().biome();
			out.add("Biome: " + capitalize((biome == null ? "unknown" : biome).replace('_', ' ')));
		}
		return out;
	}

	private static String capitalize(String s) {
		StringBuilder b = new StringBuilder();
		for (String w : s.split(" ")) {
			if (w.isEmpty()) continue;
			if (b.length() > 0) b.append(' ');
			b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return b.toString();
	}
}
