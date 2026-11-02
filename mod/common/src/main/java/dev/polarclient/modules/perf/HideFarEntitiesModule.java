package dev.polarclient.modules.perf;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.NumberSetting;

/**
 * Stops drawing mobs, armour stands, dropped items and the like past a set distance, for FPS in busy lobbies. Only the
 * drawing: they're still there (and still hittable when you walk up to them). Each version asks {@link #hides} from the
 * game's "should this entity be drawn" check.
 */
public class HideFarEntitiesModule extends Module {
	public final NumberSetting distance = add(new NumberSetting("distance", "Hide beyond", 32, 8, 128, 4, " blocks"));
	public final BooleanSetting players = add(new BooleanSetting("players", "Players too", false));

	public HideFarEntitiesModule() {
		super("hide_far_entities", "Hide Far Entities", "Hide far mobs for more FPS.", Category.PERFORMANCE, false);
	}

	/** distSq: squared distance from the camera. */
	public boolean hides(boolean isPlayer, double distSq) {
		if (!isEnabled() || (isPlayer && !players.on())) return false;
		double d = distance.get();
		return distSq > d * d;
	}
}
