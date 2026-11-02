package dev.polarclient.modules;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.gfx.Gfx;
import dev.polarclient.setting.ColorSetting;
import dev.polarclient.setting.NumberSetting;
import dev.polarclient.util.CombatTracker;
import dev.polarclient.util.Draw;

/** A hit marker around the crosshair when you hit something. */
public class HitIndicatorModule extends Module {
	private final ColorSetting color = add(new ColorSetting("color", "Color", 0xFFFFFFFF));
	private final NumberSetting size = add(new NumberSetting("size", "Size", 4, 2, 8, 1));
	private final NumberSetting time = add(new NumberSetting("time", "Show for", 0.25, 0.1, 1, 0.05, "s"));

	public HitIndicatorModule() {
		super("hit_indicator", "Hit Indicator", "Show a marker when you land a hit.", Category.COMBAT, false);
	}

	@Override
	public void renderOverlay(Gfx g) {
		long ms = CombatTracker.msSinceLastHit();
		long life = Math.round(time.get() * 1000);
		if (ms > life || !p().hasPlayer() || p().screenOpen()) return;
		float a = 1f - ms / (float) life;
		int c = Draw.alpha(color.argb(), a);
		int cx = g.width() / 2, cy = g.height() / 2, s = size.getInt(), gap = 3;
		for (int i = 0; i < s; i++) {
			g.fill(cx - gap - 1 - i, cy - gap - 1 - i, cx - gap - i, cy - gap - i, c);
			g.fill(cx + gap + i, cy - gap - 1 - i, cx + gap + 1 + i, cy - gap - i, c);
			g.fill(cx - gap - 1 - i, cy + gap + i, cx - gap - i, cy + gap + 1 + i, c);
			g.fill(cx + gap + i, cy + gap + i, cx + gap + 1 + i, cy + gap + 1 + i, c);
		}
	}
}
