package dev.polarclient.modules;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.BooleanSetting;

/** Turns the round shadows under mobs and players on or off. */
public class EntityShadowsModule extends Module {
	private final BooleanSetting shadows = add(new BooleanSetting("shadows", "Show shadows", false));
	private Boolean saved;

	public EntityShadowsModule() {
		super("entity_shadows", "Entity Shadows", "Toggle the shadows under entities.", Category.VISUAL, false);
	}

	@Override
	public void tick() {
		if (!isEnabled()) return;
		if (saved == null) saved = p().entityShadows();
		if (p().entityShadows() != shadows.on()) p().setEntityShadows(shadows.on());
	}

	@Override
	protected void onDisable() {
		if (saved != null) p().setEntityShadows(saved);
		saved = null;
	}
}
