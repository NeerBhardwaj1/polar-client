package dev.polarclient.modules.hud;

import dev.polarclient.module.Category;
import dev.polarclient.module.TextHudModule;

import java.util.List;

/** Shows the biome you are in. */
public class BiomeModule extends TextHudModule {
	public BiomeModule() {
		super("biome", "Biome Display", "Show the biome you're in.", Category.HUD, false, 4, 120);
	}

	static String pretty(String id) {
		if (id == null || id.isEmpty()) return "Unknown";
		StringBuilder b = new StringBuilder();
		for (String part : id.split("_")) {
			if (part.isEmpty()) continue;
			if (b.length() > 0) b.append(' ');
			b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return b.toString();
	}

	@Override
	protected List<String> lines(boolean preview) {
		return one("Biome: " + (preview && !p().inWorld() ? "Snowy Plains" : pretty(p().biome())));
	}
}
