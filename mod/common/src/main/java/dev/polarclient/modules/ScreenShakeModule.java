package dev.polarclient.modules;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.BooleanSetting;

/** Controls view bobbing (the camera sway while walking). */
public class ScreenShakeModule extends Module {
	private final BooleanSetting bobbing = add(new BooleanSetting("bobbing", "View bobbing", false));
	private Boolean saved;

	public ScreenShakeModule() {
		super("screen_shake", "Screen Shake Control", "Turn off view bobbing and sway.", Category.ACCESSIBILITY, false);
	}

	@Override
	public void tick() {
		if (!isEnabled()) return;
		if (saved == null) saved = p().viewBobbing();
		if (p().viewBobbing() != bobbing.on()) p().setViewBobbing(bobbing.on());
	}

	@Override
	protected void onDisable() {
		if (saved != null) p().setViewBobbing(saved);
		saved = null;
	}
}
