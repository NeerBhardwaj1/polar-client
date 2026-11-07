package dev.polarclient.cosmetics;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.NumberSetting;

/**
 * Polar cosmetics in game: capes, wings, hats, back items and auras that Polar players picked in the launcher's
 * wardrobe, and the Polar badge next to their names. Only other Polar players see them.
 */
public class PolarCosmeticsModule extends Module {
	private final BooleanSetting others = add(new BooleanSetting("show_others", "Show other players' cosmetics", true));
	private final BooleanSetting own = add(new BooleanSetting("show_own", "Show my cosmetics", true));
	private final BooleanSetting auras = add(new BooleanSetting("auras", "Show auras", true));
	private final BooleanSetting badges = add(new BooleanSetting("badges", "Show the Polar badge next to names", true));
	private final BooleanSetting animateWings = add(new BooleanSetting("animate_wings", "Animate wings", true));
	private final BooleanSetting wingParticles = add(new BooleanSetting("wing_particles", "Wing particles", true));
	private final NumberSetting wingSize = add(new NumberSetting("wing_size", "Wing size", 100, 80, 120, 5, "%"));

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

	/** Off: wings hold their resting pose (their glow and colours still move). */
	boolean animateWings() {
		return animateWings.on();
	}

	boolean wingParticles() {
		return wingParticles.on();
	}

	/** How big wings are drawn, 0.8 .. 1.2. */
	float wingScale() {
		return (float) (wingSize.get() / 100.0);
	}

	@Override
	public void tick() {
		Cosmetics.tick();
	}
}
