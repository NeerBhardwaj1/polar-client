package dev.polarclient.modules.hud;

import dev.polarclient.module.Category;
import dev.polarclient.module.TextHudModule;
import dev.polarclient.platform.ItemKind;

import java.util.List;

/** Totems of undying in your inventory. */
public class TotemCounterModule extends TextHudModule {
	public TotemCounterModule() {
		super("totem_counter", "Totem Counter", "Count totems in your inventory.", Category.COMBAT, false, 214, 172);
	}

	@Override
	public String unavailableReason() {
		return p().minecraftVersion().startsWith("1.8") ? "Totems are only in Minecraft 1.11 and newer." : null;
	}

	@Override
	protected List<String> lines(boolean preview) {
		int n = p().hasPlayer() ? p().countItems(ItemKind.TOTEM) : 2;
		return one("Totems: " + n);
	}
}
