package dev.polarclient.modules.hud;

import dev.polarclient.gfx.Gfx;
import dev.polarclient.module.HudModule;
import dev.polarclient.platform.BossBarView;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.NumberSetting;
import dev.polarclient.util.Draw;

import java.util.Collections;
import java.util.List;

/** Boss bars as a HUD element: move them, scale them, flat bars with an optional percentage. */
public class BossBarModule extends HudModule {
	private static final int W = 182;
	public final NumberSetting thickness = add(new NumberSetting("thickness", "Bar height", 5, 2, 10, 1, "px"));
	public final BooleanSetting showName = add(new BooleanSetting("show_name", "Show name", true));
	public final BooleanSetting showPercent = add(new BooleanSetting("show_percent", "Show percent", false));
	public final BooleanSetting notches = add(new BooleanSetting("notches", "Show notches", true));

	public BossBarModule() {
		super("boss_bar", "Boss Bar", "Move, scale and restyle boss bars.", false, 149, 3);
	}

	private List<BossBarView> bars(boolean preview) {
		List<BossBarView> bars = p().bossBars();
		if (!bars.isEmpty() || !preview) return bars;
		return Collections.singletonList(new BossBarView("Ender Dragon", 0.72f, 0, 0));
	}

	private int rowHeight() {
		return (showName.on() ? 10 : 0) + thickness.getInt() + 4;
	}

	@Override
	public boolean hasContent() {
		return !p().bossBars().isEmpty();
	}

	@Override
	public int width(boolean preview) {
		return W;
	}

	@Override
	public int height(boolean preview) {
		return Math.max(1, bars(preview).size()) * rowHeight();
	}

	@Override
	protected void draw(Gfx g, boolean preview) {
		int y = 0, t = thickness.getInt();
		for (BossBarView bar : bars(preview)) {
			if (showName.on()) {
				String pct = showPercent.on() ? "  " + Math.round(bar.progress * 100) + "%" : "";
				int nw = p().richWidth(bar.name) + Draw.width(pct);
				float x = (W - nw) / 2f;
				g.richLine(bar.name, x, y, 0xFFFFFFFF, true);
				if (!pct.isEmpty()) g.text(pct, x + p().richWidth(bar.name), y, 0xFFCFE5FF, true);
				y += 10;
			}
			int r = Math.min(3, t / 2);
			Draw.round(g, 0, y, W, t, r, Draw.alpha(Draw.lerp(bar.color, 0xFF000000, 0.65f), 200));
			int fw = Math.round(W * bar.progress);
			if (fw > 0) Draw.round(g, 0, y, fw, t, r, bar.color);
			if (notches.on() && bar.notches > 0) {
				for (int i = 1; i < bar.notches; i++) {
					int nx = Math.round(W * i / (float) bar.notches);
					g.fill(nx, y, nx + 1, y + t, 0x66000000);
				}
			}
			y += t + 4;
		}
	}
}
