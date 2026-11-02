package dev.polarclient.modules;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;

/** See in the dark: lights the world as if you had night vision (applied in the lightmap mixin). */
public class FullbrightModule extends Module {
	public FullbrightModule() {
		super("fullbright", "Fullbright", "Everything fully lit, even in caves and at night.", Category.VISUAL, false);
	}
}
