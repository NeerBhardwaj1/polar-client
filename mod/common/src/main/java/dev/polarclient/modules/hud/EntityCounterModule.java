package dev.polarclient.modules.hud;

import dev.polarclient.module.Category;
import dev.polarclient.module.TextHudModule;

import java.util.List;

/** How many entities are loaded around you. */
public class EntityCounterModule extends TextHudModule {
	public EntityCounterModule() {
		super("entity_counter", "Entity Counter", "Count loaded entities nearby.", Category.HUD, false, 4, 156);
	}

	@Override
	protected List<String> lines(boolean preview) {
		return one("Entities: " + (preview && !p().inWorld() ? 87 : p().entityCount()));
	}
}
