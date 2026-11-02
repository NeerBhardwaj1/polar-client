package dev.polarclient.modules.visual;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;

/** No fog under water: see as far as on land. */
public class ClearWaterModule extends Module {
	public ClearWaterModule() {
		super("clear_water", "Clear Water", "Removes the fog under water so you can see far.", Category.VISUAL, false);
	}
}
