package dev.polarclient.modules.hud;

import dev.polarclient.gfx.Gfx;
import dev.polarclient.module.HudModule;
import dev.polarclient.platform.TabListView;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ColorSetting;
import dev.polarclient.util.Draw;

import java.util.UUID;

/** The player list (Tab) as a HUD element, with heads, ping in milliseconds and your own colours. */
public class TabListModule extends HudModule {
	private static final int ROW = 10, MAX_ROWS = 20, HEADER_MAX_W = 360;
	public final ColorSetting background = add(new ColorSetting("background", "Background", 0xB00B1426, true));
	public final BooleanSetting heads = add(new BooleanSetting("heads", "Player heads", true));
	public final BooleanSetting pingNumbers = add(new BooleanSetting("ping_numbers", "Ping in ms", true));
	public final BooleanSetting headerFooter = add(new BooleanSetting("header_footer", "Header and footer", true));

	public TabListModule() {
		super("tab_list", "Tab List", "A movable player list with heads and ping in milliseconds.", false, 160, 26);
	}

	private TabListView data(boolean preview) {
		TabListView t = p().tabList();
		if (!preview) return t;
		if (t != null && !t.entries.isEmpty()) return t;
		TabListView demo = new TabListView("§b§lPolar SMP", "§7play.polar.example");
		String[] names = {"Polar", "Frosty", "Aurora", "Glacier", "Tundra", "Blizzard"};
		int[] pings = {24, 61, 140, 38, 220, 75};
		for (int i = 0; i < names.length; i++) {
			demo.entries.add(new TabListView.Entry(names[i], names[i], UUID.nameUUIDFromBytes(names[i].getBytes()), pings[i], false));
		}
		return demo;
	}

	@Override
	public boolean hasContent() {
		if (!p().tabListKeyDown()) return false;
		TabListView t = p().tabList();
		return t != null && (!p().isSingleplayer() || t.entries.size() > 1);
	}

	private int cols(TabListView t) {
		return Math.max(1, (t.entries.size() + MAX_ROWS - 1) / MAX_ROWS);
	}

	private int rows(TabListView t) {
		int c = cols(t);
		return Math.max(1, (t.entries.size() + c - 1) / c);
	}

	private int colWidth(TabListView t) {
		int name = 0;
		for (TabListView.Entry e : t.entries) name = Math.max(name, p().richWidth(e.name));
		return (heads.on() ? 10 : 0) + name + 6 + (pingNumbers.on() ? Draw.width("999ms") : 12);
	}

	private int headerLines(Object text) {
		return headerFooter.on() && text != null ? p().richLineCount(text, HEADER_MAX_W) : 0;
	}

	private int headerWidth(Object text) {
		return headerFooter.on() && text != null ? Math.min(HEADER_MAX_W, p().richWidth(text)) : 0;
	}

	@Override
	public int width(boolean preview) {
		TabListView t = data(preview);
		if (t == null) return 100;
		int body = cols(t) * colWidth(t) + (cols(t) - 1) * 4;
		return Math.max(body, Math.max(headerWidth(t.header), headerWidth(t.footer))) + 8;
	}

	@Override
	public int height(boolean preview) {
		TabListView t = data(preview);
		if (t == null) return 20;
		int hl = headerLines(t.header), fl = headerLines(t.footer);
		return 8 + rows(t) * ROW + (hl > 0 ? hl * 9 + 4 : 0) + (fl > 0 ? fl * 9 + 4 : 0);
	}

	@Override
	protected void draw(Gfx g, boolean preview) {
		TabListView t = data(preview);
		if (t == null) return;
		int w = width(preview), h = height(preview);
		Draw.round(g, 0, 0, w, h, 4, background.argb());
		int y = 4;
		int hl = headerLines(t.header);
		if (hl > 0) {
			drawCentered(g, t.header, w, y, hl);
			y += hl * 9 + 4;
		}
		int cw = colWidth(t), rows = rows(t);
		int bodyW = cols(t) * cw + (cols(t) - 1) * 4;
		int x0 = (w - bodyW) / 2;
		for (int i = 0; i < t.entries.size(); i++) {
			TabListView.Entry e = t.entries.get(i);
			int col = i / rows, row = i % rows;
			int x = x0 + col * (cw + 4), ry = y + row * ROW;
			g.fill(x, ry, x + cw, ry + ROW - 1, 0x22FFFFFF);
			int nx = x + 1;
			if (heads.on()) {
				g.playerHead(e.id, e.profileName, x + 1, ry + 0.5f, 8);
				nx += 10;
			}
			g.richLine(e.name, nx, ry + 1, e.spectator ? 0xB0FFFFFF : 0xFFFFFFFF, true);
			drawPing(g, e.ping, x + cw - 1, ry + 1);
		}
		y += rows * ROW + 4;
		int fl = headerLines(t.footer);
		if (fl > 0) drawCentered(g, t.footer, w, y, fl);
	}

	private void drawCentered(Gfx g, Object text, int w, int y, int lines) {
		int tw = headerWidth(text);
		g.richText(text, (w - tw) / 2f, y, HEADER_MAX_W, lines, 9, 0xFFFFFFFF);
	}

	private void drawPing(Gfx g, int ping, int right, int y) {
		int color = ping < 0 ? 0xFF8A9BBA : ping < 80 ? 0xFF6EE7A0 : ping < 150 ? 0xFFFFE066 : ping < 300 ? 0xFFFFA94D : 0xFFFF6B6B;
		if (pingNumbers.on()) {
			String s = ping < 0 ? "?" : ping + "ms";
			g.text(s, right - Draw.width(s), y, color, true);
		} else {
			int bars = ping < 0 ? 0 : ping < 150 ? 5 : ping < 300 ? 4 : ping < 600 ? 3 : ping < 1000 ? 2 : 1;
			for (int b = 0; b < 5; b++) {
				int bh = 2 + b, bx = right - 10 + b * 2;
				g.fill(bx, y + 7 - bh, bx + 1, y + 7, b < bars ? color : 0x55FFFFFF);
			}
		}
	}
}
