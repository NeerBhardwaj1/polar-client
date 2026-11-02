package dev.polarclient.modules.hud;

import dev.polarclient.module.Category;
import dev.polarclient.module.TextHudModule;
import dev.polarclient.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;

/** The resource packs you have on. */
public class PackDisplayModule extends TextHudModule {
	private final NumberSetting maxPacks = add(new NumberSetting("max", "Packs to list", 3, 1, 8, 1));

	public PackDisplayModule() {
		super("pack_display", "Pack Display", "Show your active resource packs.", Category.HUD, false, 4, 200);
	}

	@Override
	protected List<String> lines(boolean preview) {
		List<String> packs = p().resourcePacks();
		if (packs.isEmpty() && preview) packs = java.util.Arrays.asList("Polar 16x", "Low Fire");
		List<String> out = new ArrayList<String>();
		if (packs.isEmpty()) {
			out.add("Pack: Default");
			return out;
		}
		for (int i = 0; i < packs.size() && i < maxPacks.getInt(); i++) out.add((i == 0 ? "Pack: " : "") + packs.get(i));
		if (packs.size() > maxPacks.getInt()) out.add("+" + (packs.size() - maxPacks.getInt()) + " more");
		return out;
	}
}
