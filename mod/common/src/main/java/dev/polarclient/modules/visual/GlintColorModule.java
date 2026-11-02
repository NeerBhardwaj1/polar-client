package dev.polarclient.modules.visual;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.ColorSetting;

/** The colour of the shimmer on enchanted items and armour. */
public class GlintColorModule extends Module {
	public final ColorSetting color = add(new ColorSetting("color", "Colour", 0xFF4FA3FF));
	private int applied = -1;
	private int ticks;

	public GlintColorModule() {
		super("glint_color", "Glint Colour", "Changes the colour of the enchantment shimmer (Chroma cycles through colours).", Category.VISUAL, false);
	}

	@Override
	public void tick() {
		if (!p().hasPlayer()) return;
		ticks++;
		int want = isEnabled() ? color.argb() & 0xFFFFFF : -1;
		// chroma: re-tint a few times a second, not every tick
		if (isEnabled() && color.chroma() && ticks % 3 != 0) return;
		// now and then even when unchanged: a resource reload brings back the game's own glint
		if (want != applied || (isEnabled() && ticks % 40 == 0)) {
			p().setGlintColor(want);
			applied = want;
		}
	}
}
