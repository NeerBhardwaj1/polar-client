package dev.polarclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import dev.polarclient.PolarClient;
import dev.polarclient.gui.ui.Fonts;
import dev.polarclient.gui.ui.Icon;
import dev.polarclient.gui.ui.Theme;
import dev.polarclient.gui.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.server.LanServer;
import net.minecraft.client.server.LanServerDetection;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Polar's server list (replaces Minecraft's Multiplayer screen): saved servers as cards with icon, MOTD, players and
 * ping, LAN worlds below, Polar buttons, and the account card for switching accounts before joining.
 */
public class PolarMultiplayerScreen extends Screen {
	private static final AtomicInteger THREADS = new AtomicInteger();
	private static final ExecutorService PINGERS = Executors.newFixedThreadPool(5, r -> {
		Thread t = new Thread(r, "Polar Server Pinger #" + THREADS.incrementAndGet());
		t.setDaemon(true);
		return t;
	});

	private static final float HEADER_H = 46, FOOTER_H = 40, CARD_H = 38, LAN_H = 30, GAP = 4, BTN_H = 22;
	private static final int GLASS = 0xA60A1630, GLASS_HOVER = 0xC0142650, EDGE = 0x8C9CC9FF;
	private static final int TEXT_DIM = 0xFF9DB4D6;

	private final Screen lastScreen;
	private final ServerStatusPinger pinger = new ServerStatusPinger();
	private ServerList servers;
	private final List<Entry> entries = new ArrayList<>();
	private final Map<ServerData, Entry> entryCache = new HashMap<>();
	private List<LanServer> lan = List.of();
	private LanServerDetection.LanServerList lanList;
	private LanServerDetection.LanServerDetector lanDetector;

	private Object selected; // Entry or LanServer
	private ServerData editing;
	private float scroll, scrollTarget;
	private long lastClickTime;
	private Object lastClicked;
	private AccountCard account;
	private final List<Btn> buttons = new ArrayList<>();
	private final Map<String, Float> hover = new HashMap<>();

	private record Btn(String id, String icon, String label, boolean primary, Runnable run) {
	}

	/** One saved server: keeps its favicon texture between frames. */
	private final class Entry {
		final ServerData data;
		final FaviconTexture icon;
		byte[] lastIconBytes;

		Entry(ServerData data) {
			this.data = data;
			this.icon = FaviconTexture.forServer(minecraft.getTextureManager(), data.ip);
		}

		void ping() {
			if (data.state() != ServerData.State.INITIAL) return;
			data.setState(ServerData.State.PINGING);
			data.motd = CommonComponents.EMPTY;
			data.status = CommonComponents.EMPTY;
			PINGERS.submit(() -> {
				try {
					pinger.pingServer(data, () -> minecraft.execute(() -> servers.save()), () -> data.setState(
						data.protocol == SharedConstants.getCurrentVersion().protocolVersion() ? ServerData.State.SUCCESSFUL : ServerData.State.INCOMPATIBLE),
						EventLoopGroupHolder.remote(minecraft.options.useNativeTransport()));
				} catch (UnknownHostException e) {
					data.setState(ServerData.State.UNREACHABLE);
					data.motd = Component.translatable("multiplayer.status.cannot_resolve").withStyle(ChatFormatting.RED);
				} catch (Exception e) {
					data.setState(ServerData.State.UNREACHABLE);
					data.motd = Component.translatable("multiplayer.status.cannot_connect").withStyle(ChatFormatting.RED);
				}
			});
		}

		void updateIcon() {
			byte[] bytes = data.getIconBytes();
			if (Arrays.equals(bytes, lastIconBytes)) return;
			try {
				if (bytes == null) icon.clear();
				else icon.upload(NativeImage.read(bytes));
				lastIconBytes = bytes;
			} catch (Throwable t) {
				PolarClient.LOGGER.warn("Invalid icon for server {}", data.name);
				data.setIconBytes(null);
				lastIconBytes = null;
				servers.save();
			}
		}
	}

	public PolarMultiplayerScreen(Screen lastScreen) {
		super(Component.translatable("multiplayer.title"));
		this.lastScreen = lastScreen;
	}

	// ------------------------------------------------------------------ setup

	@Override
	protected void init() {
		// like vanilla, (re)load from disk each time the screen opens again (after Add/Edit/Delete) so pings restart
		String keepIp = selected instanceof Entry e ? e.data.ip : null;
		closeIcons();
		entries.clear();
		entryCache.clear();
		servers = new ServerList(minecraft);
		servers.load();
		if (lanDetector == null) {
			lanList = new LanServerDetection.LanServerList();
			lan = List.of();
			try {
				lanDetector = new LanServerDetection.LanServerDetector(lanList);
				lanDetector.start();
			} catch (Exception e) {
				PolarClient.LOGGER.warn("Unable to start LAN server detection", e);
			}
		}
		rebuildEntries();
		selected = null;
		for (Entry e : entries) {
			if (e.data.ip.equals(keepIp)) selected = e;
		}
		account = new AccountCard(List.of(
			new AccountCard.Shortcut(Icon.MODS, "Polar Mods", () -> minecraft.gui.setScreen(new PolarMenuScreen(this))),
			new AccountCard.Shortcut(Icon.BRUSH, "Skin layers", () -> minecraft.gui.setScreen(new SkinCustomizationScreen(this, minecraft.options)))),
			this::refreshAll);
		buttons.clear();
		buttons.add(new Btn("join", Icon.PLAY, "Join Server", true, this::joinSelected));
		buttons.add(new Btn("direct", Icon.LINK, "Direct", false, this::directConnect));
		buttons.add(new Btn("add", Icon.ADD, "Add", false, this::addServer));
		buttons.add(new Btn("edit", Icon.EDIT, "Edit", false, this::editServer));
		buttons.add(new Btn("delete", Icon.DELETE, "Delete", false, this::deleteServer));
		buttons.add(new Btn("refresh", Icon.REFRESH, "Refresh", false, this::refreshAll));
	}

	private void rebuildEntries() {
		List<Entry> next = new ArrayList<>();
		Map<ServerData, Entry> keep = new HashMap<>();
		for (int i = 0; i < servers.size(); i++) {
			ServerData d = servers.get(i);
			Entry e = entryCache.containsKey(d) ? entryCache.get(d) : new Entry(d);
			keep.put(d, e);
			next.add(e);
		}
		for (Map.Entry<ServerData, Entry> old : entryCache.entrySet()) {
			if (!keep.containsKey(old.getKey()) && !old.getValue().icon.isClosed()) old.getValue().icon.close();
		}
		entryCache.clear();
		entryCache.putAll(keep);
		entries.clear();
		entries.addAll(next);
		if (selected instanceof Entry e && !entries.contains(e)) selected = null;
	}

	/** Re-pings everything (also used after switching accounts). */
	private void refreshAll() {
		minecraft.gui.setScreen(new PolarMultiplayerScreen(lastScreen));
	}

	@Override
	public void tick() {
		super.tick();
		List<LanServer> found = lanList == null ? null : lanList.takeDirtyServers();
		if (found != null) {
			lan = List.copyOf(found);
			if (selected instanceof LanServer l && !lan.contains(l)) selected = null;
		}
		pinger.tick();
	}

	@Override
	public void removed() {
		if (lanDetector != null) {
			lanDetector.interrupt();
			lanDetector = null;
		}
		pinger.removeAll();
		closeIcons();
	}

	private void closeIcons() {
		for (Entry e : entries) {
			if (!e.icon.isClosed()) e.icon.close();
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(lastScreen);
	}

	// ------------------------------------------------------------------ actions

	private boolean canJoin() {
		return selected != null;
	}

	private boolean canEdit() {
		return selected instanceof Entry;
	}

	private void join(ServerData data) {
		ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(data.ip), data, false, null);
	}

	private void joinSelected() {
		if (selected instanceof Entry e) join(e.data);
		else if (selected instanceof LanServer l) join(new ServerData(l.getMotd(), l.getAddress(), ServerData.Type.LAN));
	}

	private void directConnect() {
		editing = new ServerData(I18n.get("selectServer.defaultName"), "", ServerData.Type.OTHER);
		minecraft.gui.setScreen(new DirectJoinServerScreen(this, ok -> {
			if (!ok) {
				minecraft.gui.setScreen(this);
				return;
			}
			ServerData existing = servers.get(editing.ip);
			if (existing == null) {
				servers.add(editing, true);
				servers.save();
				join(editing);
			} else {
				join(existing);
			}
		}, editing));
	}

	private void addServer() {
		editing = new ServerData("", "", ServerData.Type.OTHER);
		minecraft.gui.setScreen(new ManageServerScreen(this, Component.translatable("manageServer.add.title"), ok -> {
			if (ok) {
				ServerData hidden = servers.unhide(editing.ip);
				if (hidden != null) hidden.copyNameIconFrom(editing);
				else servers.add(editing, false);
				servers.save();
			}
			minecraft.gui.setScreen(this);
		}, editing));
	}

	private void editServer() {
		if (!(selected instanceof Entry e)) return;
		ServerData current = e.data;
		editing = new ServerData(current.name, current.ip, ServerData.Type.OTHER);
		editing.copyFrom(current);
		minecraft.gui.setScreen(new ManageServerScreen(this, Component.translatable("manageServer.edit.title"), ok -> {
			if (ok) {
				current.name = editing.name;
				current.ip = editing.ip;
				current.setState(ServerData.State.INITIAL);
				current.setResourcePackStatus(editing.getResourcePackStatus());
				servers.save();
			}
			minecraft.gui.setScreen(this);
		}, editing));
	}

	private void deleteServer() {
		if (!(selected instanceof Entry e)) return;
		minecraft.gui.setScreen(new ConfirmScreen(ok -> {
			if (ok) {
				servers.remove(e.data);
				servers.save();
				selected = null;
			}
			minecraft.gui.setScreen(this);
		}, Component.translatable("selectServer.deleteQuestion"), Component.translatable("selectServer.deleteWarning", e.data.name),
			Component.translatable("selectServer.deleteButton"), CommonComponents.GUI_CANCEL));
	}

	private void move(Entry e, int dir) {
		int i = entries.indexOf(e), j = i + dir;
		if (i < 0 || j < 0 || j >= entries.size()) return;
		servers.swap(i, j);
		servers.save();
		rebuildEntries();
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
		return Fonts.width(b.label(), b.primary() ? Fonts.BOLD : Fonts.BODY) + (icons ? 30 : 18);
	}

	private float buttonsWidth(boolean icons) {
		float w = 0;
		for (Btn b : buttons) w += buttonWidth(b, icons) + 6;
		return w - 6;
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if (minecraft.level == null) {
			MenuBackground.INSTANCE.render(g, width, height, mouseX, mouseY);
			g.fill(0, 0, width, height, 0x40000000);
		} else {
			extractBlurredBackground(g);
			g.fill(0, 0, width, height, 0x80000000);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Ui.frame();
		scroll = Ui.approach(scroll, scrollTarget, 18f);
		int mx = account.isOpen() ? -1 : mouseX, my = account.isOpen() ? -1 : mouseY;

		drawHeader(g, mx, my);
		drawList(g, mx, my);
		drawButtons(g, mx, my);
		account.render(g, width, mouseX, mouseY);
		super.extractRenderState(g, mouseX, mouseY, a);
		if (account.isOpen()) g.nextStratum(); // keep the dropdown above everything drawn before it
		account.renderMenu(g, width, mouseX, mouseY);
	}

	private float hov(String key, boolean on) {
		float v = Ui.approach(hover.getOrDefault(key, 0f), on ? 1f : 0f, 14f);
		hover.put(key, v);
		return v;
	}

	private void drawHeader(GuiGraphicsExtractor g, int mx, int my) {
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

	private void drawList(GuiGraphicsExtractor g, int mx, int my) {
		float px = panelX(), pw = panelW(), top = listTop(), bottom = listBottom();
		Ui.rect(g, px, top, pw, bottom - top, 10, 0x8C07101F);
		Ui.ring(g, px, top, pw, bottom - top, 10, 0x339CC9FF, 1f);
		scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll()));
		boolean inList = Ui.inside(mx, my, px, top, pw, bottom - top);
		g.enableScissor((int) px, (int) top + 3, (int) (px + pw), (int) bottom - 3);

		float x = px + 6, w = pw - 12 - (maxScroll() > 0 ? 5 : 0), y = top + 6 - scroll;
		if (entries.isEmpty()) {
			Ui.textCentered(g, "No servers yet", px + pw / 2f, y + 16, 0xFFFFFFFF, Fonts.BOLD);
			Ui.textCentered(g, "Add one, or use Direct to join once.", px + pw / 2f, y + 29, TEXT_DIM, Fonts.SMALL);
			y += 46;
		}
		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);
			if (y + CARD_H > top && y < bottom) drawServer(g, e, i, x, y, w, inList ? mx : -1, inList ? my : -1);
			y += CARD_H + GAP;
		}
		// LAN
		Ui.textSpaced(g, "LAN WORLDS", x + 4, y + 9, 0xFF7F93B5, Fonts.SMALL, 0.8f);
		if (lan.isEmpty()) {
			int dots = (int) (Util.getMillis() / 400 % 4);
			Ui.text(g, "Scanning" + ".".repeat(dots), x + 4 + Ui.spacedWidth("LAN WORLDS", Fonts.SMALL, 0.8f) + 8, y + 9 - Fonts.cap(Fonts.SMALL) / 2f, TEXT_DIM, Fonts.SMALL);
		}
		y += 20;
		for (LanServer l : lan) {
			if (y + LAN_H > top && y < bottom) drawLan(g, l, x, y, w, inList ? mx : -1, inList ? my : -1);
			y += LAN_H + GAP;
		}
		g.disableScissor();

		if (maxScroll() > 0) {
			float trackH = bottom - top - 12, thumbH = Math.max(18, trackH * trackH / (trackH + maxScroll()));
			float thumbY = top + 6 + (trackH - thumbH) * (scroll / maxScroll());
			Ui.rect(g, px + pw - 7, thumbY, 3, thumbH, 1.5f, 0x669CC9FF);
		}
	}

	private void card(GuiGraphicsExtractor g, String key, boolean sel, float x, float y, float w, float h, boolean hovered) {
		float hv = hov(key, hovered);
		Ui.rect(g, x, y, w, h, 7, sel ? 0xB8143A6E : Theme.lerp(GLASS, GLASS_HOVER, hv));
		Ui.ring(g, x, y, w, h, 7, sel ? 0xFF8CC8FF : Theme.lerp(0x409CC9FF, 0x99CFE9FF, hv), sel ? 1.5f : 1f);
	}

	private void drawServer(GuiGraphicsExtractor g, Entry e, int index, float x, float y, float w, int mx, int my) {
		ServerData d = e.data;
		e.ping();
		e.updateIcon();
		boolean hovered = Ui.inside(mx, my, x, y, w, CARD_H);
		card(g, "s" + System.identityHashCode(d), selected == e, x, y, w, CARD_H, hovered);

		// favicon (hover: play overlay to join)
		int ix = Math.round(x + 4), iy = Math.round(y + 3);
		g.blit(RenderPipelines.GUI_TEXTURED, e.icon.textureLocation(), ix, iy, 0f, 0f, 32, 32, 32, 32);
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
		switch (d.state()) {
			case SUCCESSFUL -> {
				drawBars(g, right - 13, statusY, bars(d.ping), 0xFF8CC8FF);
				String ms = d.ping + " ms";
				Ui.textRight(g, ms, right - 17, statusY, TEXT_DIM, Fonts.SMALL);
				if (d.players != null) {
					String pl = d.players.online() + "/" + d.players.max();
					float pr = right - 21 - Fonts.width(ms, Fonts.SMALL);
					Ui.textRight(g, pl, pr, statusY, 0xFFFFFFFF, Fonts.BODY);
					float pw = Fonts.width(pl, Fonts.BODY);
					if (!d.playerList.isEmpty() && Ui.inside(mx, my, pr - pw, statusY - 5, pw, 10)) {
						List<FormattedCharSequence> lines = new ArrayList<>();
						for (Component c : d.playerList) lines.add(c.getVisualOrderText());
						g.setTooltipForNextFrame(lines, mx, my);
					}
				}
			}
			case INCOMPATIBLE -> {
				Ui.icon(g, Icon.WARNING, right - 6, statusY, 0xFFFFC857, Fonts.ICON_M);
				Ui.textRight(g, d.version.getString(), right - 15, statusY, 0xFFFFC857, Fonts.SMALL);
			}
			case UNREACHABLE -> {
				Ui.icon(g, Icon.WIFI_OFF, right - 6, statusY, 0xFFFF7A7A, Fonts.ICON_M);
				Ui.textRight(g, "Offline", right - 15, statusY, 0xFFFF7A7A, Fonts.SMALL);
			}
			default -> {
				int n = (int) (Util.getMillis() / 120 % 8);
				drawBars(g, right - 13, statusY, n > 4 ? 8 - n : n, 0x669CC9FF);
			}
		}

		// name + MOTD (MOTD keeps its own colours, so it uses Minecraft's font)
		float tx = x + 42, maxW = right - 60 - tx;
		Ui.text(g, Fonts.ellipsize(d.name, Fonts.BOLD, (int) Math.max(30, maxW)), tx, y + 6, 0xFFFFFFFF, Fonts.BOLD);
		List<FormattedCharSequence> motd = font.split(d.motd, (int) Math.max(40, x + w - 10 - tx));
		for (int i = 0; i < Math.min(2, motd.size()); i++) g.text(font, motd.get(i), Math.round(tx), Math.round(y + 16 + i * 9), 0xFF8A9BBA, false);
	}

	private void arrow(GuiGraphicsExtractor g, String icon, float cx, float cy, int mx, int my) {
		boolean h = Ui.inside(mx, my, cx - 6, cy - 7, 12, 14);
		if (h) Ui.circle(g, cx, cy, 6.5f, 0x33FFFFFF);
		Ui.icon(g, icon, cx, cy, h ? 0xFFFFFFFF : TEXT_DIM, Fonts.ICON_M);
	}

	private static int bars(long ping) {
		return ping < 150 ? 5 : ping < 300 ? 4 : ping < 600 ? 3 : ping < 1000 ? 2 : 1;
	}

	private static void drawBars(GuiGraphicsExtractor g, float x, float midY, int lit, int color) {
		for (int i = 0; i < 5; i++) {
			float h = 2.5f + i * 1.4f;
			Ui.rect(g, x + i * 2.8f, midY + 4 - h, 2f, h, 0.8f, i < lit ? color : 0x40FFFFFF);
		}
	}

	private void drawLan(GuiGraphicsExtractor g, LanServer l, float x, float y, float w, int mx, int my) {
		boolean hovered = Ui.inside(mx, my, x, y, w, LAN_H);
		card(g, "l" + l.getAddress(), selected == l, x, y, w, LAN_H, hovered);
		Ui.rect(g, x + 5, y + 4, 22, 22, 6, 0x408CC8FF);
		Ui.icon(g, Icon.LAN, x + 16, y + 15, 0xFF8CC8FF, Fonts.ICON_M);
		Ui.text(g, Fonts.ellipsize(l.getMotd(), Fonts.BOLD, (int) (w - 50)), x + 34, y + 6, 0xFFFFFFFF, Fonts.BOLD);
		String addr = minecraft.options.hideServerAddress ? "Address hidden" : l.getAddress();
		Ui.text(g, addr, x + 34, y + 18, TEXT_DIM, Fonts.SMALL);
	}

	private void drawButtons(GuiGraphicsExtractor g, int mx, int my) {
		boolean icons = showButtonIcons();
		float x = width / 2f - buttonsWidth(icons) / 2f, y = height - FOOTER_H / 2f - BTN_H / 2f;
		for (Btn b : buttons) {
			float bw = buttonWidth(b, icons);
			boolean enabled = enabled(b);
			float hv = hov("b" + b.id(), enabled && Ui.inside(mx, my, x, y, bw, BTN_H));
			int fg;
			if (b.primary()) {
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
				Ui.icon(g, b.icon(), x + 14, y + BTN_H / 2f, fg, Fonts.ICON_M);
				tx = x + 23;
			}
			Ui.textMid(g, b.label(), tx, y + BTN_H / 2f, fg, b.primary() ? Fonts.BOLD : Fonts.BODY);
			x += bw + 6;
		}
	}

	private boolean enabled(Btn b) {
		return switch (b.id()) {
			case "join" -> canJoin();
			case "edit", "delete" -> canEdit();
			default -> true;
		};
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
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
					b.run().run();
				}
				return true;
			}
			bx += bw + 6;
		}
		// list
		float px = panelX(), pw = panelW(), top = listTop(), bottom = listBottom();
		if (!Ui.inside(mx, my, px, top + 3, pw, bottom - top - 6)) return super.mouseClicked(event, doubleClick);
		float x = px + 6, w = pw - 12 - (maxScroll() > 0 ? 5 : 0), y = top + 6 - scroll;
		if (entries.isEmpty()) y += 46;
		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);
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
					select(e);
					join(e.data);
					return true;
				}
				return selectOrJoin(e);
			}
			y += CARD_H + GAP;
		}
		y += 20;
		for (LanServer l : lan) {
			if (Ui.inside(mx, my, x, y, w, LAN_H)) return selectOrJoin(l);
			y += LAN_H + GAP;
		}
		return true;
	}

	private void select(Object o) {
		selected = o;
	}

	/** Click selects; a second click within 300 ms joins. */
	private boolean selectOrJoin(Object o) {
		long now = Util.getMillis();
		boolean dbl = o == lastClicked && now - lastClickTime < 300;
		lastClicked = o;
		lastClickTime = now;
		selected = o;
		if (dbl) joinSelected();
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - (float) scrollY * 24));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (account.isOpen() && event.isEscape()) {
			account.close();
			return true;
		}
		if (event.key() == InputConstants.KEY_F5) {
			refreshAll();
			return true;
		}
		if (event.isConfirmation() && canJoin()) {
			joinSelected();
			return true;
		}
		if (event.key() == InputConstants.KEY_DELETE && canEdit()) {
			deleteServer();
			return true;
		}
		if (event.isUp() || event.isDown()) {
			List<Object> all = new ArrayList<>(entries);
			all.addAll(lan);
			if (!all.isEmpty()) {
				int i = all.indexOf(selected);
				i = i < 0 ? 0 : Math.max(0, Math.min(all.size() - 1, i + (event.isUp() ? -1 : 1)));
				if (event.hasShiftDown() && selected instanceof Entry e && all.get(i) instanceof Entry) {
					move(e, event.isUp() ? -1 : 1);
				} else {
					selected = all.get(i);
				}
				return true;
			}
		}
		return super.keyPressed(event);
	}

	private void click() {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
	}
}
