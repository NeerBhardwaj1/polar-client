package dev.polarclient.modules;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.NumberSetting;
import net.minecraft.client.Minecraft;

/** Removes (or reduces) the screen shake when you take damage, using the game's Damage Tilt option. */
public class NoHurtCamModule extends Module {
	private final NumberSetting strength = add(new NumberSetting("strength", "Shake strength", 0, 0, 100, 5, "%"));
	private Double saved;

	public NoHurtCamModule() {
		super("no_hurt_cam", "No Hurt Cam", "Stops your camera shaking when you get hit.", Category.COMBAT, false);
	}

	@Override
	public void tick(Minecraft mc) {
		if (mc.options == null) return;
		var tilt = mc.options.damageTiltStrength();
		if (isEnabled()) {
			if (saved == null) saved = tilt.get();
			double want = strength.get() / 100.0;
			if (Math.abs(tilt.get() - want) > 1e-6) tilt.set(want);
		} else if (saved != null) {
			tilt.set(saved);
			saved = null;
		}
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (saved != null && mc.options != null) {
			mc.options.damageTiltStrength().set(saved);
			saved = null;
		}
	}
}
