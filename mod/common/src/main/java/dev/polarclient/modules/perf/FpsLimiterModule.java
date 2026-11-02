package dev.polarclient.modules.perf;

import dev.polarclient.module.Category;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.NumberSetting;

/** Caps the frame rate, with a lower cap while the game is in the background. */
public class FpsLimiterModule extends dev.polarclient.module.Module {
	private final NumberSetting limit = add(new NumberSetting("limit", "FPS limit", 144, 10, 260, 1));
	private final BooleanSetting background = add(new BooleanSetting("background", "Lower FPS when the game isn't focused", true));
	private final NumberSetting backgroundFps = add(new NumberSetting("background_fps", "Background FPS", 15, 1, 60, 1));
	private int saved = -1;
	private int applied = -1;

	public FpsLimiterModule() {
		super("fps_limiter", "FPS Limiter", "Cap FPS, lower in the background.", Category.PERFORMANCE, false);
		backgroundFps.visibleWhen(() -> background.on());
	}

	@Override
	public void tick() {
		if (!isEnabled()) {
			restore();
			return;
		}
		if (saved < 0) saved = p().maxFps();
		int want = limit.getInt() >= 260 ? 260 : limit.getInt();
		if (background.on() && !p().windowFocused()) want = Math.min(want, backgroundFps.getInt());
		if (want != applied) {
			p().setMaxFps(want);
			applied = want;
		}
	}

	private void restore() {
		if (saved >= 0) {
			p().setMaxFps(saved);
			saved = -1;
			applied = -1;
		}
	}

	@Override
	protected void onDisable() {
		restore();
	}
}
