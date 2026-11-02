package dev.polarclient.modules;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.NumberSetting;

/**
 * Removes (or reduces) the screen shake when you take damage: through the game's Damage Tilt option where it exists
 * (1.19.4+), otherwise the version's hurt-camera mixin scales the shake by {@link #shakeScale()}.
 */
public class NoHurtCamModule extends Module {
	private final NumberSetting strength = add(new NumberSetting("strength", "Shake strength", 0, 0, 100, 5, "%"));
	private Double saved;

	public NoHurtCamModule() {
		super("no_hurt_cam", "No Hurt Cam", "Disable hurt camera shake.", Category.VISUAL, false);
	}

	/** Multiplier for the vanilla hurt shake on versions without the Damage Tilt option (1 = unchanged). */
	public float shakeScale() {
		return isEnabled() ? (float) (strength.get() / 100.0) : 1f;
	}

	@Override
	public void tick() {
		if (!p().hasDamageTiltOption()) return;
		if (isEnabled()) {
			if (saved == null) saved = p().damageTilt();
			double want = strength.get() / 100.0;
			if (Math.abs(p().damageTilt() - want) > 1e-6) p().setDamageTilt(want);
		} else if (saved != null) {
			p().setDamageTilt(saved);
			saved = null;
		}
	}

	@Override
	protected void onDisable() {
		if (saved != null && p() != null && p().hasDamageTiltOption()) {
			p().setDamageTilt(saved);
			saved = null;
		}
	}
}
