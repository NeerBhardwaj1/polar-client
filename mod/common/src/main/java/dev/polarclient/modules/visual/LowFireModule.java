package dev.polarclient.modules.visual;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.NumberSetting;

/** Lowers the fire overlay you see while burning, so it covers less of the screen. */
public class LowFireModule extends Module {
	public final NumberSetting lower = add(new NumberSetting("lower", "Lower by", 40, 0, 100, 5, "%"));

	public LowFireModule() {
		super("low_fire", "Low Fire", "Lowers the fire on your screen while you burn.", Category.VISUAL, false);
	}

	/** How far down to move the fire quads, in their own units (the screen is about 2 tall). */
	public float offset() {
		return (float) (lower.get() / 100.0 * 0.45);
	}
}
