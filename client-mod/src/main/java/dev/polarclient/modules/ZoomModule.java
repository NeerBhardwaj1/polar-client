package dev.polarclient.modules;

import dev.polarclient.PolarKeys;
import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.NumberSetting;
import dev.polarclient.util.Draw;
import net.minecraft.client.Minecraft;

/** Hold the zoom key (C by default) to zoom in; scroll while zooming to adjust. */
public class ZoomModule extends Module {
	private final NumberSetting factor = add(new NumberSetting("factor", "Zoom level", 4, 1.5, 20, 0.5, "x"));
	private final BooleanSetting smooth = add(new BooleanSetting("smooth", "Smooth zoom", true));
	private final BooleanSetting scroll = add(new BooleanSetting("scroll", "Scroll to adjust", true));
	private final BooleanSetting cinematic = add(new BooleanSetting("cinematic", "Cinematic camera while zoomed", false));

	private boolean zooming;
	private float current = 1f;
	private float scrollMultiplier = 1f;
	private long lastNanos = System.nanoTime();
	private Boolean savedSmoothCamera;

	public ZoomModule() {
		super("zoom", "Zoom", "Hold C to zoom like OptiFine. Scroll to zoom further.", Category.UTILITY, true);
	}

	@Override
	public void tick(Minecraft mc) {
		boolean want = isEnabled() && mc.player != null && PolarKeys.ZOOM.isDown();
		if (want && !zooming) scrollMultiplier = 1f;
		zooming = want;
		if (zooming && cinematic.on()) {
			if (savedSmoothCamera == null) savedSmoothCamera = mc.options.smoothCamera;
			mc.options.smoothCamera = true;
		} else if (savedSmoothCamera != null) {
			mc.options.smoothCamera = savedSmoothCamera;
			savedSmoothCamera = null;
		}
	}

	public boolean isZooming() {
		return zooming;
	}

	/** Called from the camera every frame with the vanilla FOV. */
	public float modifyFov(float fov) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNanos) / 1e9f);
		lastNanos = now;
		float target = zooming ? Math.max(1f, Math.min(50f, factor.getFloat() * scrollMultiplier)) : 1f;
		current = smooth.on() ? Draw.approach(current, target, 12f, dt) : target;
		if (Math.abs(current - 1f) < 0.001f) current = 1f;
		return fov / current;
	}

	/** Mouse wheel while zooming. Returns true to stop the hotbar from scrolling. */
	public boolean onScroll(double amount) {
		if (!zooming || !scroll.on() || amount == 0) return false;
		scrollMultiplier *= amount > 0 ? 1.2f : 1f / 1.2f;
		float total = factor.getFloat() * scrollMultiplier;
		if (total < 1.1f) scrollMultiplier = 1.1f / factor.getFloat();
		if (total > 50f) scrollMultiplier = 50f / factor.getFloat();
		return true;
	}
}
