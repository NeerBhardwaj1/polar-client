package dev.polarclient.cosmetics;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.BooleanSetting;

/**
 * Polar cosmetics in game: capes, wings, hats, back items and auras that Polar players picked in the launcher's
 * wardrobe, and the Polar badge next to their names. Only other Polar players see them.
 */
public class PolarCosmeticsModule extends Module {
	private final BooleanSetting others = add(new BooleanSetting("show_others", "Show other players' cosmetics", true));
	private final BooleanSetting own = add(new BooleanSetting("show_own", "Show my cosmetics", true));
	private final BooleanSetting auras = add(new BooleanSetting("auras", "Show auras", true));
	private final BooleanSetting badges = add(new BooleanSetting("badges", "Show the Polar badge next to names", true));

	public PolarCosmeticsModule() {
		super("polar_cosmetics", "Polar Cosmetics", "Capes, wings, hats and auras from the Polar wardrobe.", Category.CUSTOMIZATION, true);
	}

	boolean showOthers() {
		return others.on();
	}

	boolean showOwn() {
		return own.on();
	}

	boolean showAuras() {
		return auras.on();
	}

	boolean showBadges() {
		return badges.on();
	}

	@Override
	public void tick() {
		Cosmetics.tick();
	}
}
