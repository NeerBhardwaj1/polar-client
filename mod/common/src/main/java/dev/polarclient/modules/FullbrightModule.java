package dev.polarclient.modules;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;

/** See in the dark: lights the world as if you had night vision (applied in the lightmap mixin). */
public class FullbrightModule extends Module {
	public FullbrightModule() {
		super("fullbright", "Fullbright", "Brightens dark areas and caves.", Category.VISUAL, false);
	}
}
