package dev.polarclient.gui;

import dev.polarclient.gfx.Gfx;
import dev.polarclient.gui.ui.Fonts;
import dev.polarclient.gui.ui.Icon;
import dev.polarclient.gui.ui.Theme;
import dev.polarclient.gui.ui.Ui;
import dev.polarclient.platform.ServerBackend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Polar's server list (replaces Minecraft's Multiplayer screen): saved servers as cards with icon, MOTD, players and
 * ping, LAN worlds below, Polar buttons, and the account card for switching accounts before joining.
 */
public class MultiplayerView extends View {
	private static final float HEADER_H = 46, FOOTER_H = 40, CARD_H = 38, LAN_H = 30, GAP = 4, BTN_H = 22;
	private static final int GLASS = 0xA60A1630, GLASS_HOVER = 0xC0142650, EDGE = 0x8C9CC9FF;
	private static final int TEXT_DIM = 0xFF9DB4D6;

	private ServerBackend backend;
	private List<ServerBackend.Server> entries = Collections.emptyList();
	private List<ServerBackend.Lan> lan = Collections.emptyList();

	private Object selected; // ServerBackend.Server or ServerBackend.Lan
	private float scroll, scrollTarget;
	private long lastClickTime;
	private Object lastClicked;
	private AccountCard account;
	private final List<Btn> buttons = new ArrayList<Btn>();
	private final Map<String, Float> hover = new HashMap<String, Float>();

	private static final class Btn {
		final String id, icon, label;
		final boolean primary;
		final Runnable run;

		Btn(String id, String icon, String label, boolean primary, Runnable run) {
			this.id = id;
			this.icon = icon;
			this.label = label;
			this.primary = primary;
			this.run = run;
		}
	}

	public MultiplayerView(Object parent) {
		super(parent);
	}

	@Override
	public String title() {
		return "Multiplayer";
	}

	// ------------------------------------------------------------------ setup

	@Override
	protected void init() {
		// like vanilla, (re)load from disk each time the screen opens again (after Add/Edit/Delete) so pings restart
		String keepIp = selected instanceof ServerBackend.Server ? ((ServerBackend.Server) selected).address() : null;
		if (backend == null) backend = p().createServerBackend(p().currentScreen());
		backend.load();
		entries = backend.servers();
		selected = null;
		for (ServerBackend.Server e : entries) {
			if (e.address().equals(keepIp)) selected = e;
		}
		account = new AccountCard(Arrays.asList(
			new AccountCard.Shortcut(Icon.MODS, "Polar Mods", () -> p().open(new PolarMenuView(p().currentScreen()))),
			new AccountCard.Shortcut(Icon.BRUSH, "Skin layers", () -> p().openSkinOptions(p().currentScreen()))),
			this::refreshAll);
		buttons.clear();
		buttons.add(new Btn("join", Icon.PLAY, "Join Server", true, this::joinSelected));
		buttons.add(new Btn("direct", Icon.LINK, "Direct", false, () -> backend.openDirect()));
		buttons.add(new Btn("add", Icon.ADD, "Add", false, () -> backend.openAdd()));
		buttons.add(new Btn("edit", Icon.EDIT, "Edit", false, this::editServer));
		buttons.add(new Btn("delete", Icon.DELETE, "Delete", false, this::deleteServer));
		buttons.add(new Btn("refresh", Icon.REFRESH, "Refresh", false, this::refreshAll));
	}

	/** Re-pings everything (also used after switching accounts). */
	private void refreshAll() {
		closeBackend();
		p().open(new MultiplayerView(parent));
	}

	@Override
	public void tick() {
		if (backend == null) return;
		backend.tick();
		lan = backend.lan();
		if (selected instanceof ServerBackend.Lan && !lan.contains(selected)) selected = null;
	}

	@Override
	public void removed() {
		// the view is also "removed" while a vanilla dialog (Add, Edit, ...) is open; the backend survives that,
		// and is closed when this list is left for good
	}

	private void closeBackend() {
		if (backend != null) {
			backend.close();
			backend = null;
		}
	}

	@Override
	public void onClose() {
		closeBackend();
		p().show(parent);
	}

	// ------------------------------------------------------------------ actions

	private boolean canJoin() {
		return selected != null;
	}

	private boolean canEdit() {
		return selected instanceof ServerBackend.Server;
	}

	private void joinSelected() {
		if (backend == null) return;
		if (selected instanceof ServerBackend.Server) backend.join((ServerBackend.Server) selected);
		else if (selected instanceof ServerBackend.Lan) backend.joinLan((ServerBackend.Lan) selected);
	}

	private void editServer() {
		if (selected instanceof ServerBackend.Server) backend.openEdit((ServerBackend.Server) selected);
	}

	private void deleteServer() {
		if (selected instanceof ServerBackend.Server) backend.openDelete((ServerBackend.Server) selected);
	}

	private void move(ServerBackend.Server e, int dir) {
		int i = entries.indexOf(e), j = i + dir;
		if (i < 0 || j < 0 || j >= entries.size()) return;
		backend.swap(i, j);
		entries = backend.servers();
	}

	// ------------------------------------------------------------------ layout

	private float panelW() {
		return Math.min(width - 24, 430);
	}

	private float panelX() {
		return (width - panelW()) / 2f;
	}

	private float listTop() {
		return HEADER_H + 2;
	}

	private float listBottom() {
		return height - FOOTER_H - 4;
	}

	private float contentHeight() {
		float h = entries.isEmpty() ? 46 : entries.size() * (CARD_H + GAP);
		h += 20; // LAN header
		h += lan.size() * (LAN_H + GAP);
		return h + 4;
	}

	private float maxScroll() {
		return Math.max(0, contentHeight() - (listBottom() - listTop() - 12));
	}

	private boolean showButtonIcons() {
		return buttonsWidth(true) <= width - 20;
	}

	private float buttonWidth(Btn b, boolean icons) {
		return Fonts.width(b.label, b.primary ? Fonts.BOLD : Fonts.BODY) + (icons ? 30 : 18);
	}

	private float buttonsWidth(boolean icons) {
		float w = 0;
		for (Btn b : buttons) w += buttonWidth(b, icons) + 6;
		return w - 6;
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void renderBackground(Gfx g, int mouseX, int mouseY) {
		if (!p().inWorld()) {
			MenuBackground.INSTANCE.render(g, width, height, mouseX, mouseY);
			g.fill(0, 0, width, height, 0x40000000);
		} else {
			g.blurBackground();
			g.fill(0, 0, width, height, 0x80000000);
		}
	}

	@Override
	public void render(Gfx g, int mouseX, int mouseY, float a) {
		Ui.frame();
		scroll = Ui.approach(scroll, scrollTarget, 18f);
		int mx = account.isOpen() ? -1 : mouseX, my = account.isOpen() ? -1 : mouseY;

		drawHeader(g, mx, my);
		drawList(g, mx, my);
		drawButtons(g, mx, my);
		account.render(g, width, mouseX, mouseY);
		if (tooltip != null) {
			drawTooltip(g, tooltip, tooltipX, tooltipY);
			tooltip = null;
		}
		if (account.isOpen()) g.nextLayer(); // keep the dropdown above everything drawn before it
		account.renderMenu(g, width, mouseX, mouseY);
	}

	private List<String> tooltip;
	private int tooltipX, tooltipY;

	/** Player list tooltip, drawn above the list. */
	private void drawTooltip(Gfx g, List<String> lines, int mx, int my) {
		int w = 0;
		for (String l : lines) w = Math.max(w, p().textWidth(l, dev.polarclient.gfx.UiFont.VANILLA));
		int h = lines.size() * 10 + 6, x = mx + 10, y = my - 12;
		if (x + w + 8 > width) x = mx - w - 16;
		if (y + h > height) y = height - h - 2;
		g.nextLayer();
		Ui.rect(g, x, y, w + 8, h, 4, 0xF0100A1E);
		Ui.ring(g, x, y, w + 8, h, 4, 0x805028FF, 1f);
		for (int i = 0; i < lines.size(); i++) g.text(lines.get(i), x + 4, y + 4 + i * 10, 0xFFFFFFFF, true);
	}

	private float hov(String key, boolean on) {
		Float prev = hover.get(key);
		float v = Ui.approach(prev == null ? 0f : prev, on ? 1f : 0f, 14f);
		hover.put(key, v);
		return v;
	}

	private void drawHeader(Gfx g, int mx, int my) {
		float bx = 12, by = 13, bs = 22;
		String sub = entries.size() == 1 ? "1 server" : entries.size() + " servers";
		if (!lan.isEmpty()) sub += "  ·  " + lan.size() + " on LAN";
		// glass plate so the title stays readable over the animated sky
		float plateW = bs + 18 + Math.max(Fonts.width("Multiplayer", Fonts.LARGE), Fonts.width(sub, Fonts.SMALL));
		Ui.rect(g, bx - 4, by - 4, plateW, bs + 8, 15, 0x9907101F);
		float hv = hov("back", Ui.inside(mx, my, bx, by, bs, bs));
		Ui.circle(g, bx + bs / 2f, by + bs / 2f, bs / 2f, Theme.lerp(GLASS, GLASS_HOVER, hv));
		Ui.ring(g, bx, by, bs, bs, bs / 2f, Theme.lerp(EDGE, 0xE6CFE9FF, hv), 1.2f);
		Ui.icon(g, Icon.BACK, bx + bs / 2f, by + bs / 2f, 0xFFFFFFFF, Fonts.ICON_M);
		Ui.text(g, "Multiplayer", bx + bs + 9, by + 3, 0xFFFFFFFF, Fonts.LARGE);
		Ui.text(g, sub, bx + bs + 10, by + 15, TEXT_DIM, Fonts.SMALL);
	}

	private void drawList(Gfx g, int mx, int my) {
		float px = panelX(), pw = panelW(), top = listTop(), bottom = listBottom();
		Ui.rect(g, px, top, pw, bottom - top, 10, 0x8C07101F);
		Ui.ring(g, px, top, pw, bottom - top, 10, 0x339CC9FF, 1f);
		scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll()));
		boolean inList = Ui.inside(mx, my, px, top, pw, bottom - top);
		g.scissor((int) px, (int) top + 3, (int) (px + pw), (int) bottom - 3);

		float x = px + 6, w = pw - 12 - (maxScroll() > 0 ? 5 : 0), y = top + 6 - scroll;
		if (entries.isEmpty()) {
			Ui.textCentered(g, "No servers yet", px + pw / 2f, y + 16, 0xFFFFFFFF, Fonts.BOLD);
			Ui.textCentered(g, "Add one, or use Direct to join once.", px + pw / 2f, y + 29, TEXT_DIM, Fonts.SMALL);
			y += 46;
		}
		for (int i = 0; i < entries.size(); i++) {
			ServerBackend.Server e = entries.get(i);
			if (y + CARD_H > top && y < bottom) drawServer(g, e, i, x, y, w, inList ? mx : -1, inList ? my : -1);
			y += CARD_H + GAP;
		}
		// LAN
		Ui.textSpaced(g, "LAN WORLDS", x + 4, y + 9, 0xFF7F93B5, Fonts.SMALL, 0.8f);
		if (lan.isEmpty()) {
			int dots = (int) (System.currentTimeMillis() / 400 % 4);
			Ui.text(g, "Scanning" + "...".substring(0, dots), x + 4 + Ui.spacedWidth("LAN WORLDS", Fonts.SMALL, 0.8f) + 8, y + 9 - Fonts.cap(Fonts.SMALL) / 2f, TEXT_DIM, Fonts.SMALL);
		}
		y += 20;
		for (ServerBackend.Lan l : lan) {
			if (y + LAN_H > top && y < bottom) drawLan(g, l, x, y, w, inList ? mx : -1, inList ? my : -1);
			y += LAN_H + GAP;
		}
		g.endScissor();

		if (maxScroll() > 0) {
			float trackH = bottom - top - 12, thumbH = Math.max(18, trackH * trackH / (trackH + maxScroll()));
			float thumbY = top + 6 + (trackH - thumbH) * (scroll / maxScroll());
			Ui.rect(g, px + pw - 7, thumbY, 3, thumbH, 1.5f, 0x669CC9FF);
		}
	}

	private void card(Gfx g, String key, boolean sel, float x, float y, float w, float h, boolean hovered) {
		float hv = hov(key, hovered);
		Ui.rect(g, x, y, w, h, 7, sel ? 0xB8143A6E : Theme.lerp(GLASS, GLASS_HOVER, hv));
		Ui.ring(g, x, y, w, h, 7, sel ? 0xFF8CC8FF : Theme.lerp(0x409CC9FF, 0x99CFE9FF, hv), sel ? 1.5f : 1f);
	}

	private void drawServer(Gfx g, ServerBackend.Server d, int index, float x, float y, float w, int mx, int my) {
		backend.ping(d);
		boolean hovered = Ui.inside(mx, my, x, y, w, CARD_H);
		card(g, "s" + System.identityHashCode(d), selected == d, x, y, w, CARD_H, hovered);

		// favicon (hover: play overlay to join)
		int ix = Math.round(x + 4), iy = Math.round(y + 3);
		Object icon = d.icon();
		if (icon != null) g.blitHandle(icon, ix, iy, 32, 32);
		if (hovered && Ui.inside(mx, my, ix, iy, 32, 32)) {
			g.fill(ix, iy, ix + 32, iy + 32, 0x99000000);
			Ui.icon(g, Icon.PLAY, ix + 16, iy + 16, 0xFFFFFFFF, Fonts.ICON_M);
		}

		// right side: reorder arrows on hover, players, ping
		float right = x + w - 8;
		if (hovered) {
			if (index > 0) arrow(g, Icon.ARROW_UP, right - 6, y + 10, mx, my);
			if (index < entries.size() - 1) arrow(g, Icon.ARROW_DOWN, right - 6, y + 28, mx, my);
			right -= 16;
		}
		float statusY = y + 9;
		switch (d.status()) {
			case SUCCESSFUL: {
				drawBars(g, right - 13, statusY, bars(d.ping()), 0xFF8CC8FF);
				String ms = d.ping() + " ms";
				Ui.textRight(g, ms, right - 17, statusY, TEXT_DIM, Fonts.SMALL);
				if (d.hasPlayerCount()) {
					String pl = d.online() + "/" + d.max();
					float pr = right - 21 - Fonts.width(ms, Fonts.SMALL);
					Ui.textRight(g, pl, pr, statusY, 0xFFFFFFFF, Fonts.BODY);
					float pw = Fonts.width(pl, Fonts.BODY);
					if (!d.playerSample().isEmpty() && Ui.inside(mx, my, pr - pw, statusY - 5, pw, 10)) {
						tooltip = d.playerSample();
						tooltipX = mx;
						tooltipY = my;
					}
				}
				break;
			}
			case INCOMPATIBLE:
				Ui.icon(g, Icon.WARNING, right - 6, statusY, 0xFFFFC857, Fonts.ICON_M);
				Ui.textRight(g, d.versionName(), right - 15, statusY, 0xFFFFC857, Fonts.SMALL);
				break;
			case UNREACHABLE:
				Ui.icon(g, Icon.WIFI_OFF, right - 6, statusY, 0xFFFF7A7A, Fonts.ICON_M);
				Ui.textRight(g, "Offline", right - 15, statusY, 0xFFFF7A7A, Fonts.SMALL);
				break;
			default: {
				int n = (int) (System.currentTimeMillis() / 120 % 8);
				drawBars(g, right - 13, statusY, n > 4 ? 8 - n : n, 0x669CC9FF);
			}
		}

		// name + MOTD (MOTD keeps its own colours, so it uses Minecraft's font)
		float tx = x + 42, maxW = right - 60 - tx;
		Ui.text(g, Fonts.ellipsize(d.name(), Fonts.BOLD, (int) Math.max(30, maxW)), tx, y + 6, 0xFFFFFFFF, Fonts.BOLD);
		g.richText(d.motd(), Math.round(tx), Math.round(y + 16), (int) Math.max(40, x + w - 10 - tx), 2, 9, 0xFF8A9BBA);
	}

	private void arrow(Gfx g, String icon, float cx, float cy, int mx, int my) {
		boolean h = Ui.inside(mx, my, cx - 6, cy - 7, 12, 14);
		if (h) Ui.circle(g, cx, cy, 6.5f, 0x33FFFFFF);
		Ui.icon(g, icon, cx, cy, h ? 0xFFFFFFFF : TEXT_DIM, Fonts.ICON_M);
	}

	private static int bars(long ping) {
		return ping < 150 ? 5 : ping < 300 ? 4 : ping < 600 ? 3 : ping < 1000 ? 2 : 1;
	}

	private static void drawBars(Gfx g, float x, float midY, int lit, int color) {
		for (int i = 0; i < 5; i++) {
			float h = 2.5f + i * 1.4f;
			Ui.rect(g, x + i * 2.8f, midY + 4 - h, 2f, h, 0.8f, i < lit ? color : 0x40FFFFFF);
		}
	}

	private void drawLan(Gfx g, ServerBackend.Lan l, float x, float y, float w, int mx, int my) {
		boolean hovered = Ui.inside(mx, my, x, y, w, LAN_H);
		card(g, "l" + l.address(), selected == l, x, y, w, LAN_H, hovered);
		Ui.rect(g, x + 5, y + 4, 22, 22, 6, 0x408CC8FF);
		Ui.icon(g, Icon.LAN, x + 16, y + 15, 0xFF8CC8FF, Fonts.ICON_M);
		Ui.text(g, Fonts.ellipsize(l.motd(), Fonts.BOLD, (int) (w - 50)), x + 34, y + 6, 0xFFFFFFFF, Fonts.BOLD);
		String addr = p().hideServerAddresses() ? "Address hidden" : l.address();
		Ui.text(g, addr, x + 34, y + 18, TEXT_DIM, Fonts.SMALL);
	}

	private void drawButtons(Gfx g, int mx, int my) {
		boolean icons = showButtonIcons();
		float x = width / 2f - buttonsWidth(icons) / 2f, y = height - FOOTER_H / 2f - BTN_H / 2f;
		for (Btn b : buttons) {
			float bw = buttonWidth(b, icons);
			boolean enabled = enabled(b);
			float hv = hov("b" + b.id, enabled && Ui.inside(mx, my, x, y, bw, BTN_H));
			int fg;
			if (b.primary) {
				int top = enabled ? Theme.mix(0xFFD7EEFF, 0xFFFFFFFF, 0.25f * hv) : 0xFF51647F;
				int bot = enabled ? Theme.mix(0xFF8FCBFF, 0xFFFFFFFF, 0.18f * hv) : 0xFF3B4A63;
				if (enabled) Ui.glow(g, x + bw / 2f, y + BTN_H / 2f, bw * 0.75f, BTN_H * 1.2f, Theme.alpha(0xFF7CC4FF, 0.25f + 0.15f * hv));
				Ui.gradientRect(g, x, y, bw, BTN_H, 6, top, bot);
				fg = enabled ? 0xFF0B1A33 : 0xFF9DB4D6;
			} else {
				Ui.rect(g, x, y, bw, BTN_H, 6, Theme.lerp(GLASS, GLASS_HOVER, hv));
				Ui.ring(g, x, y, bw, BTN_H, 6, Theme.lerp(EDGE, 0xE6CFE9FF, hv), 1.2f);
				fg = enabled ? 0xFFFFFFFF : 0x66FFFFFF;
			}
			float tx = x + 9;
			if (icons) {
				Ui.icon(g, b.icon, x + 14, y + BTN_H / 2f, fg, Fonts.ICON_M);
				tx = x + 23;
			}
			Ui.textMid(g, b.label, tx, y + BTN_H / 2f, fg, b.primary ? Fonts.BOLD : Fonts.BODY);
			x += bw + 6;
		}
	}

	private boolean enabled(Btn b) {
		if (b.id.equals("join")) return canJoin();
		if (b.id.equals("edit") || b.id.equals("delete")) return canEdit();
		return true;
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button != LEFT) return false;
		if (account.mouseClicked(mx, my, width)) return true;
		if (Ui.inside(mx, my, 12, 13, 22, 22)) {
			click();
			onClose();
			return true;
		}
		// bottom buttons
		boolean icons = showButtonIcons();
		float bx = width / 2f - buttonsWidth(icons) / 2f, by = height - FOOTER_H / 2f - BTN_H / 2f;
		for (Btn b : buttons) {
			float bw = buttonWidth(b, icons);
			if (Ui.inside(mx, my, bx, by, bw, BTN_H)) {
				if (enabled(b)) {
					click();
					b.run.run();
				}
				return true;
			}
			bx += bw + 6;
		}
		// list
		float px = panelX(), pw = panelW(), top = listTop(), bottom = listBottom();
		if (!Ui.inside(mx, my, px, top + 3, pw, bottom - top - 6)) return false;
		float x = px + 6, w = pw - 12 - (maxScroll() > 0 ? 5 : 0), y = top + 6 - scroll;
		if (entries.isEmpty()) y += 46;
		for (int i = 0; i < entries.size(); i++) {
			ServerBackend.Server e = entries.get(i);
			if (Ui.inside(mx, my, x, y, w, CARD_H)) {
				float right = x + w - 8;
				if (i > 0 && Ui.inside(mx, my, right - 12, y + 3, 12, 14)) {
					move(e, -1);
					return true;
				}
				if (i < entries.size() - 1 && Ui.inside(mx, my, right - 12, y + 21, 12, 14)) {
					move(e, 1);
					return true;
				}
				if (Ui.inside(mx, my, x + 4, y + 3, 32, 32)) {
					selected = e;
					backend.join(e);
					return true;
				}
				return selectOrJoin(e);
			}
			y += CARD_H + GAP;
		}
		y += 20;
		for (ServerBackend.Lan l : lan) {
			if (Ui.inside(mx, my, x, y, w, LAN_H)) return selectOrJoin(l);
			y += LAN_H + GAP;
		}
		return true;
	}

	/** Click selects; a second click within 300 ms joins. */
	private boolean selectOrJoin(Object o) {
		long now = System.currentTimeMillis();
		boolean dbl = o == lastClicked && now - lastClickTime < 300;
		lastClicked = o;
		lastClickTime = now;
		selected = o;
		if (dbl) joinSelected();
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double amount) {
		scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - (float) amount * 24));
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput key) {
		if (account.isOpen() && key.is(KeyInput.Key.ESCAPE)) {
			account.close();
			return true;
		}
		if (key.is(KeyInput.Key.F5)) {
			refreshAll();
			return true;
		}
		if (key.confirm() && canJoin()) {
			joinSelected();
			return true;
		}
		if (key.is(KeyInput.Key.DELETE) && canEdit()) {
			deleteServer();
			return true;
		}
		boolean up = key.is(KeyInput.Key.UP), down = key.is(KeyInput.Key.DOWN);
		if (up || down) {
			List<Object> all = new ArrayList<Object>(entries);
			all.addAll(lan);
			if (!all.isEmpty()) {
				int i = all.indexOf(selected);
				i = i < 0 ? 0 : Math.max(0, Math.min(all.size() - 1, i + (up ? -1 : 1)));
				if (key.shift && selected instanceof ServerBackend.Server && all.get(i) instanceof ServerBackend.Server) {
					move((ServerBackend.Server) selected, up ? -1 : 1);
				} else {
					selected = all.get(i);
				}
				return true;
			}
		}
		return false;
	}

	private void click() {
		p().playClickSound();
	}
}
