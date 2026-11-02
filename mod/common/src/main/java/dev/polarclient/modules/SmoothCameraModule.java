package dev.polarclient.modules;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;

/** Cinematic (smooth) camera. */
public class SmoothCameraModule extends Module {
	private Boolean saved;

	public SmoothCameraModule() {
		super("smooth_camera", "Smooth Camera", "Cinematic, smoothed camera movement.", Category.VISUAL, false);
	}

	@Override
	public void tick() {
		if (isEnabled() && saved == null && p().hasPlayer()) {
			saved = p().smoothCamera();
			p().setSmoothCamera(true);
		}
	}

	@Override
	protected void onDisable() {
		if (saved != null) p().setSmoothCamera(saved);
		saved = null;
	}
}
