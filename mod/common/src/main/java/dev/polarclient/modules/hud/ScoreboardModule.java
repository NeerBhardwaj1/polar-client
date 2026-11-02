package dev.polarclient.modules.hud;

import dev.polarclient.gfx.Gfx;
import dev.polarclient.module.HudModule;
import dev.polarclient.platform.Sidebar;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ColorSetting;
import dev.polarclient.util.Draw;

/** The sidebar scoreboard as a HUD element: move it, scale it, restyle it. */
public class ScoreboardModule extends HudModule {
	private static final int LINE = 9, PAD = 3;
	public final ColorSetting background = add(new ColorSetting("background", "Background", 0x660B1426, true));
	public final BooleanSetting titleBar = add(new BooleanSetting("title_bar", "Title bar", true));
	public final BooleanSetting hideNumbers = add(new BooleanSetting("hide_numbers", "Hide red numbers", true));
	public final BooleanSetting rounded = add(new BooleanSetting("rounded", "Rounded corners", true));
	public final BooleanSetting shadow = add(new BooleanSetting("shadow", "Text shadow", false));

	public ScoreboardModule() {
		super("scoreboard", "Scoreboard", "Move, scale and restyle the sidebar scoreboard, or hide its red numbers.", false, 395, 100);
	}

	private Sidebar data(boolean preview) {
		Sidebar s = p().sidebar();
		if (s != null || !preview) return s;
		Sidebar demo = new Sidebar("§b§lPOLAR SMP");
		demo.add("§7Rank: §bMember", "§c5");
		demo.add("§7Kills: §f12", "§c4");
		demo.add("§7Coins: §6840", "§c3");
		demo.add("", "§c2");
		demo.add("§bplay.polar.example", "§c1");
		return demo;
	}

	@Override
	public boolean hasContent() {
		return p().sidebar() != null;
	}

	private int contentWidth(Sidebar s) {
		int w = p().richWidth(s.title);
		for (int i = 0; i < s.size(); i++) {
			int line = p().richWidth(s.names.get(i));
			if (!hideNumbers.on()) {
				int sw = p().richWidth(s.scores.get(i));
				if (sw > 0) line += 8 + sw;
			}
			w = Math.max(w, line);
		}
		return w;
	}

	@Override
	public int width(boolean preview) {
		Sidebar s = data(preview);
		return s == null ? 60 : contentWidth(s) + PAD * 2;
	}

	@Override
	public int height(boolean preview) {
		Sidebar s = data(preview);
		return s == null ? 20 : (s.size() + 1) * LINE + PAD * 2 + 1;
	}

	@Override
	protected void draw(Gfx g, boolean preview) {
		Sidebar s = data(preview);
		if (s == null) return;
		int w = width(preview), h = height(preview);
		int r = rounded.on() ? 3 : 0;
		int bg = background.argb();
		Draw.round(g, 0, 0, w, h, r, bg);
		if (titleBar.on()) Draw.round(g, 0, 0, w, LINE + PAD + 1, r, Draw.alpha(0xFF000000, Math.min(255, (bg >>> 24) / 2 + 20)));
		int tw = p().richWidth(s.title);
		g.richLine(s.title, (w - tw) / 2f, PAD, 0xFFFFFFFF, shadow.on());
		for (int i = 0; i < s.size(); i++) {
			int y = PAD + (i + 1) * LINE + 1;
			g.richLine(s.names.get(i), PAD, y, 0xFFFFFFFF, shadow.on());
			if (!hideNumbers.on()) {
				Object score = s.scores.get(i);
				g.richLine(score, w - PAD - p().richWidth(score), y, 0xFFFFFFFF, shadow.on());
			}
		}
	}
}
