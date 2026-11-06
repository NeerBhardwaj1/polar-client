package dev.polarclient.modules.visual;

import dev.polarclient.Log;
import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.NumberSetting;

/**
 * Motion blur, the way modern games do it: while the camera turns, every pixel of the world is blurred along the path
 * it moves on screen, inside the one frame (no old frames mixed in, so no ghost copies and no lag). Your hand, the HUD
 * and menus stay sharp. It rests while an Iris shader pack is on (packs have their own). The maths is in
 * {@link dev.polarclient.render.CameraBlur}; each version draws it with its own shader.
 */
public class MotionBlurModule extends Module {
	public final NumberSetting strength = add(new NumberSetting("strength", "Strength", 50, 10, 100, 5, "%"));
	private volatile String failed;

	public MotionBlurModule() {
		super("motion_blur", "Motion Blur", "Smooth blur on fast camera turns, like modern games.", Category.VISUAL, false);
	}

	/** How long the virtual camera shutter stays open: up to 25 ms (at 100%), the same at any frame rate. */
	public float exposureSeconds() {
		return (float) (strength.get() / 100.0 * 0.025);
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
