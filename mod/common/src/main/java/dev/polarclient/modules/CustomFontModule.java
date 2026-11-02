package dev.polarclient.modules;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;

/** HUD mods drawn in Polar's smooth font. */
public class CustomFontModule extends Module {
	public CustomFontModule() {
		super("custom_font", "Custom Font", "Smooth font for your HUD mods.", Category.CUSTOMIZATION, false);
	}
}
