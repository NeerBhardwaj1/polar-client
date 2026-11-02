package dev.polarclient.modules.perf;

import dev.polarclient.gfx.Gfx;
import dev.polarclient.module.Category;
import dev.polarclient.module.HudModule;
import dev.polarclient.setting.NumberSetting;
import dev.polarclient.util.Draw;
import dev.polarclient.util.FrameStats;

import java.util.Locale;

/** A graph of recent frame times. */
public class FrameTimeModule extends HudModule {
	private final NumberSetting target = add(new NumberSetting("target", "Good frame time", 16.7, 4, 50, 0.1, " ms"));

	public FrameTimeModule() {
		super("frame_time", "Frame Time", "Graph of recent frame times.", Category.PERFORMANCE, false, 380, 4);
	}

	@Override
	public int width(boolean preview) {
		return 96;
	}

	@Override
	public int height(boolean preview) {
		return 40;
	}

	@Override
	protected void draw(Gfx g, boolean preview) {
		Draw.round(g, 0, 0, 96, 40, 3, 0x880B1426);
		int n = FrameStats.count();
		float good = target.getFloat();
		float scale = Math.max(good * 3, 1);
		for (int i = 0; i < 90; i++) {
			int idx = n - 90 + i;
			float ms = idx >= 0 ? FrameStats.sample(idx) : (preview ? good * (0.7f + 0.6f * (float) Math.abs(Math.sin(i * 0.37))) : 0);
			if (ms <= 0) continue;
			int h = Math.max(1, Math.min(26, Math.round(ms / scale * 26)));
			int color = ms <= good * 1.1f ? 0xFF5CF2A0 : ms <= good * 2 ? 0xFFFFD36B : 0xFFFF6B6B;
			g.fill(3 + i, 37 - h, 4 + i, 37, color);
		}
		float avg = n > 0 ? FrameStats.average() : good;
		g.text(String.format(Locale.ROOT, "%.1f ms", avg), 4, 3, 0xFFFFFFFF, true);
	}
}
