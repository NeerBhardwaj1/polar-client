package dev.polarclient.modules.visual;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.ColorSetting;

/** The colour mobs and players flash when they get hurt. */
public class HitColorModule extends Module {
	/** Alpha is how strong the tint is (the game's own is red at about 30%). */
	public final ColorSetting color = add(new ColorSetting("color", "Colour", 0x4DFF0000, true));
	private int applied = 0;

	public HitColorModule() {
		super("hit_color", "Hit Colour", "Changes the colour mobs and players flash when hit.", Category.VISUAL, false);
	}

	@Override
	public void tick() {
		// 0 means "the game's own colour", so a fully transparent pick is sent as almost-black-transparent
		int want = isEnabled() ? (color.argb() == 0 ? 0x00000001 : color.argb()) : 0;
		if (want != applied && p().hasPlayer()) {
			p().setHurtColor(want);
			applied = want;
		}
	}
}
