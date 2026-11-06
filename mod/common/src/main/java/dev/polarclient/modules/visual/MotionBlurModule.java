package dev.polarclient.modules.visual;

import dev.polarclient.Log;
import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.NumberSetting;

/**
 * Motion blur: every frame of the world is mixed with the frames before it, so fast turns leave a soft trail. It's
 * drawn on the world only (before the HUD and menus), so text stays sharp. Each Minecraft version draws it with its
 * own post effect (polarclient:motion_blur_10 ... motion_blur_90, one per strength step).
 */
public class MotionBlurModule extends Module {
	public final NumberSetting strength = add(new NumberSetting("strength", "Strength", 50, 10, 90, 10, "%"));
	private volatile String failed;

	public MotionBlurModule() {
		super("motion_blur", "Motion Blur", "Smooths fast camera turns with a soft blur trail.", Category.VISUAL, false);
	}

	/** The strength as one of the steps the mod ships an effect for: 10, 20, ... 90. */
	public int level() {
		int s = (int) Math.round(strength.get() / 10.0) * 10;
		return Math.max(10, Math.min(90, s));
	}

	/** How much of the previous frame stays in each new one, 0.1 to 0.9. */
	public float blend() {
		return level() / 100f;
	}

	/** The name of the post effect for the current strength ("motion_blur_50"). */
	public String effectName() {
		return "motion_blur_" + level();
	}

	/** Whether to draw the blur this frame. */
	public boolean active() {
		return isEnabled() && failed == null;
	}

	/** The effect couldn't start (usually an old graphics driver): stop trying and say why in the menu. */
	public void fail(String why, Throwable t) {
		if (failed != null) return;
		failed = "Motion blur couldn't start on this graphics card.";
		if (t != null) Log.warn("Motion blur turned off: " + why, t);
		else Log.warn("Motion blur turned off: " + why);
	}

	@Override
	public String unavailableReason() {
		return failed;
	}
}
