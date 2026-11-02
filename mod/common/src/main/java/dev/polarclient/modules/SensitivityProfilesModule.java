package dev.polarclient.modules;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.ModeSetting;
import dev.polarclient.setting.NumberSetting;

/** Three mouse sensitivity presets to switch between. */
public class SensitivityProfilesModule extends Module {
	private final ModeSetting active = add(new ModeSetting("active", "Active preset", "Normal", "Precise", "Normal", "Fast"));
	private final NumberSetting precise = add(new NumberSetting("precise", "Precise", 60, 1, 200, 1, "%"));
	private final NumberSetting normal = add(new NumberSetting("normal", "Normal", 100, 1, 200, 1, "%"));
	private final NumberSetting fast = add(new NumberSetting("fast", "Fast", 140, 1, 200, 1, "%"));
	private Double saved;

	public SensitivityProfilesModule() {
		super("sensitivity_profiles", "Sensitivity Profiles", "Switch between sensitivity presets.", Category.ACCESSIBILITY, false);
	}

	private double wanted() {
		double pct = active.is("Precise") ? precise.get() : active.is("Fast") ? fast.get() : normal.get();
		return Math.max(0, Math.min(1, pct / 200.0)); // the options slider: 0.5 = 100%
	}

	@Override
	public void tick() {
		if (!isEnabled()) return;
		if (saved == null) saved = p().sensitivity();
		if (Math.abs(p().sensitivity() - wanted()) > 1e-4) p().setSensitivity(wanted());
	}

	@Override
	protected void onDisable() {
		if (saved != null) p().setSensitivity(saved);
		saved = null;
	}
}
