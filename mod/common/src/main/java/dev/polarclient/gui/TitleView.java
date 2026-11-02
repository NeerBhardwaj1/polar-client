package dev.polarclient.gui;

import dev.polarclient.Polar;
import dev.polarclient.gfx.Gfx;
import dev.polarclient.gui.ui.Fonts;
import dev.polarclient.gui.ui.Icon;
import dev.polarclient.gui.ui.Theme;
import dev.polarclient.gui.ui.Ui;
import dev.polarclient.util.Draw;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Polar main menu: animated snowy night (see {@link MenuBackground}), glowing logo, three wide buttons,
 * round shortcuts with labels, an account card and a live Minecraft-services status.
 */
public class TitleView extends View {
	private static final float BTN_W = 172, BTN_H = 25, BTN_GAP = 7, BTN_R = 6;
	private static final float ROUND = 26, ROUND_STEP = 50;
	private static final int ICE_TOP = 0xFFD7EEFF, ICE_BOTTOM = 0xFF8FCBFF, NAVY_TEXT = 0xFF0B1A33;
	private static final int GLASS = 0xA60A1630, GLASS_HOVER = 0xC0142650, EDGE = 0x8C9CC9FF;

	private static final class Action {
		final String icon, label;
		final Runnable run;

		Action(String icon, String label, Runnable run) {
			this.icon = icon;
			this.label = label;
			this.run = run;
		}
	}

	private enum Status { CHECKING, ONLINE, OFFLINE }

	private static volatile Status status = Status.CHECKING;
	private static boolean statusRequested;

	private final List<Action> buttons = new ArrayList<Action>();
	private final List<Action> shortcuts = new ArrayList<Action>();
	private final float[] hover = new float[3];
	private final float[] roundHover = new float[4];
	private AccountCard account;
	private final long openedAt = System.currentTimeMillis();

	public TitleView() {
		super(null);
	}

	@Override
	protected void init() {
		buttons.clear();
		shortcuts.clear();
		buttons.add(new Action(Icon.TRIANGLE, "Singleplayer", () -> p().openSingleplayer(p().currentScreen())));
		buttons.add(new Action(Icon.GROUPS, "Multiplayer", () -> p().openMultiplayer(p().currentScreen())));
		buttons.add(new Action(Icon.CLOUD, "Realms", () -> p().openRealms(p().currentScreen())));
		shortcuts.add(new Action(Icon.MODS, "Mods", () -> p().open(new PolarMenuView(p().currentScreen()))));
		shortcuts.add(new Action(Icon.BRUSH, "Cosmetics", () -> p().openSkinOptions(p().currentScreen())));
		shortcuts.add(new Action(Icon.SETTINGS, "Settings", () -> p().openOptions(p().currentScreen())));
		shortcuts.add(new Action(Icon.POWER, "Exit", () -> p().quit()));
		account = new AccountCard(Arrays.asList(
			new AccountCard.Shortcut(Icon.MODS, "Polar Mods", () -> p().open(new PolarMenuView(p().currentScreen()))),
			new AccountCard.Shortcut(Icon.EDIT, "Edit HUD", () -> p().open(new HudEditorView(p().currentScreen()))),
			new AccountCard.Shortcut(Icon.BRUSH, "Skin layers", () -> p().openSkinOptions(p().currentScreen()))),
			() -> {
			});
		checkServices();
	}

	/** Pings Minecraft's auth/profile service once per session to show a real online/offline status. */
	private static void checkServices() {
		if (statusRequested) return;
		statusRequested = true;
		Thread t = new Thread(() -> {
			try {
				HttpURLConnection c = (HttpURLConnection) new URL("https://api.minecraftservices.com/").openConnection();
				c.setRequestMethod("HEAD");
				c.setConnectTimeout(5000);
				c.setReadTimeout(6000);
				c.getResponseCode();
				c.disconnect();
				status = Status.ONLINE;
			} catch (Exception e) {
				status = Status.OFFLINE;
			}
		}, "Polar Services Check");
		t.setDaemon(true);
		t.start();
	}

	@Override
	public boolean closeOnEscape() {
		return false;
	}

	// ------------------------------------------------------------------ layout

	private static final float TOP = 8, FOOTER = 40;
	private static final float BUTTONS_H = 3 * BTN_H + 2 * BTN_GAP, SHORTCUTS_H = ROUND + 16;

	/**
	 * {logoY, logoH, buttonsY, roundY}. Fits any GUI size (Auto scale at 1080p is only 480x270): the logo shrinks
	 * first, then the gaps, so the shortcuts always stay above the footer.
	 */
	private float[] layout() {
		float avail = height - TOP - FOOTER;
		float fixed = BUTTONS_H + SHORTCUTS_H;
		float logoH = Math.max(34, Math.min(70, (avail - fixed) * 0.55f));
		float free = Math.max(0, avail - fixed - logoH);
		float logoY = TOP + free * 0.24f;
		float buttonsY = logoY + logoH + Math.max(6, free * 0.38f);
		float roundY = buttonsY + BUTTONS_H + Math.max(6, free * 0.24f);
		return new float[]{logoY, logoH, buttonsY, roundY};
	}

	private float logoY() {
		return layout()[0];
	}

	private float logoH() {
		return layout()[1];
	}

	private float logoW() {
		return logoH() * Draw.WORDMARK_ASPECT;
	}

	private float buttonsY() {
		return layout()[2];
	}

	private float roundY() {
		return layout()[3];
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void renderBackground(Gfx g, int mouseX, int mouseY) {
		MenuBackground.INSTANCE.render(g, width, height, mouseX, mouseY);
	}

	@Override
	public void render(Gfx g, int mouseX, int mouseY, float a) {
		Ui.frame();
		float t = (System.currentTimeMillis() - openedAt) / 1000f;
		float intro = Ui.easeOut(t / 0.8f);
		int mx = account.isOpen() ? -1 : mouseX, my = account.isOpen() ? -1 : mouseY;

		// soft shade behind the menu column for legibility
		Ui.glow(g, width / 2f, buttonsY() + 50, 190, 120, 0x59000000);

		drawLogo(g, t, intro);
		drawButtons(g, mx, my);
		drawShortcuts(g, mx, my);
		account.render(g, width, mouseX, mouseY);
		drawFooter(g, t);
		if (account.isOpen()) g.nextLayer(); // keep the dropdown above everything drawn before it
		account.renderMenu(g, width, mouseX, mouseY);
	}

	private void drawLogo(Gfx g, float t, float intro) {
		float lh = logoH(), lw = logoW();
		float x = width / 2f - lw / 2f, y = logoY() - (1 - intro) * 8;
		// "POLAR CLIENT" lettering only: a dark backing for contrast against the aurora, then a soft ice-blue glow
		float cx = x + lw * 0.5f, cy = y + lh * 0.42f;
		float pulse = 0.8f + 0.2f * (float) Math.sin(t * 1.6);
		Ui.glow(g, cx, cy, lw * 0.62f, lh * 0.8f, 0x40000000);
		Ui.glow(g, cx, cy, lw * 0.56f, lh * 0.72f, Theme.alpha(0xFF6FC4FF, 0.24f * pulse));
		Draw.texture(g, Draw.WORDMARK, Math.round(x), Math.round(y), Math.round(lw), Math.round(lh));
		// a slow glint drifting across the ice lettering
		float gx = x + lw * (0.1f + 0.8f * (0.5f + 0.5f * (float) Math.sin(t * 0.35)));
		Ui.glow(g, gx, y + lh * 0.36f, lh * 0.55f, lh * 0.34f, Theme.alpha(0xFFDCEFFF, 0.10f));
	}

	private void drawButtons(Gfx g, int mouseX, int mouseY) {
		float x = width / 2f - BTN_W / 2f, y = buttonsY();
		for (int i = 0; i < buttons.size(); i++) {
			Action b = buttons.get(i);
			boolean primary = i == 0;
			boolean hov = Ui.inside(mouseX, mouseY, x, y, BTN_W, BTN_H);
			hover[i] = Ui.approach(hover[i], hov ? 1f : 0f, 14f);
			float hv = hover[i];
			if (primary) {
				for (int k = 5; k >= 1; k--) {
					float e = k * 1.6f;
					Ui.rect(g, x - e, y - e, BTN_W + 2 * e, BTN_H + 2 * e, BTN_R + e, Theme.alpha(0xFF7CC4FF, (0.05f + 0.03f * hv) * (6 - k) / 5f));
				}
				Ui.gradientRect(g, x, y, BTN_W, BTN_H, BTN_R, Theme.mix(ICE_TOP, 0xFFFFFFFF, 0.25f * hv), Theme.mix(ICE_BOTTOM, 0xFFFFFFFF, 0.18f * hv));
				Ui.ring(g, x, y, BTN_W, BTN_H, BTN_R, 0xCCEAF6FF, 1.5f);
			} else {
				if (hv > 0.01f) {
					for (int k = 3; k >= 1; k--) {
						float e = k * 1.5f;
						Ui.rect(g, x - e, y - e, BTN_W + 2 * e, BTN_H + 2 * e, BTN_R + e, Theme.alpha(0xFF7CC4FF, 0.05f * hv * (4 - k) / 3f));
					}
				}
				Ui.rect(g, x, y, BTN_W, BTN_H, BTN_R, Theme.lerp(GLASS, GLASS_HOVER, hv));
				Ui.ring(g, x, y, BTN_W, BTN_H, BTN_R, Theme.lerp(EDGE, 0xE6CFE9FF, hv), 1.5f);
			}
			int fg = primary ? NAVY_TEXT : 0xFFFFFFFF;
			float mid = y + BTN_H / 2f;
			Ui.icon(g, b.icon, x + 28, mid, fg, Fonts.ICON_M);
			Ui.textMid(g, b.label, x + 46, mid, fg, primary ? Fonts.BOLD : Fonts.BODY);
			Ui.icon(g, Icon.CHEVRON_RIGHT, x + BTN_W - 14 + 1.5f * hv, mid, Theme.alpha(fg, primary ? 1f : 0.85f), Fonts.ICON_M);
			y += BTN_H + BTN_GAP;
		}
	}

	private void drawShortcuts(Gfx g, int mouseX, int mouseY) {
		float cy = roundY() + ROUND / 2f;
		float x0 = width / 2f - ROUND_STEP * (shortcuts.size() - 1) / 2f;
		for (int i = 0; i < shortcuts.size(); i++) {
			Action s = shortcuts.get(i);
			float cx = x0 + i * ROUND_STEP;
			boolean hov = Ui.inside(mouseX, mouseY, cx - ROUND / 2f, cy - ROUND / 2f, ROUND, ROUND + 14);
			roundHover[i] = Ui.approach(roundHover[i], hov ? 1f : 0f, 14f);
			float hv = roundHover[i];
			if (hv > 0.01f) Ui.glow(g, cx, cy, ROUND * 0.9f, ROUND * 0.9f, Theme.alpha(0xFF7CC4FF, 0.35f * hv));
			Ui.circle(g, cx, cy, ROUND / 2f, Theme.lerp(GLASS, GLASS_HOVER, hv));
			Ui.ring(g, cx - ROUND / 2f, cy - ROUND / 2f, ROUND, ROUND, ROUND / 2f, Theme.lerp(EDGE, 0xE6CFE9FF, hv), 1.5f);
			Ui.icon(g, s.icon, cx, cy, 0xFFFFFFFF, Fonts.ICON_M);
			Ui.textCentered(g, s.label, cx, cy + ROUND / 2f + 9, Theme.alpha(0xFFFFFFFF, 0.92f), Fonts.BODY);
		}
	}

	private void drawFooter(Gfx g, float t) {
		float by = height - 12;
		Ui.vline(g, 12, by - 20, 20, 2, 0x99CFE5FF);
		Ui.textSpaced(g, ("Polar Client " + Polar.version()).toUpperCase(Locale.ROOT), 18, by - 14.5f, 0xFFFFFFFF, Fonts.BOLD, 1.1f);
		Ui.textSpaced(g, ("Minecraft " + p().minecraftVersion()).toUpperCase(Locale.ROOT), 18, by - 4.5f, 0xFF9DB4D6, Fonts.SMALL, 1.0f);

		Status st = status;
		String label = st == Status.ONLINE ? "MINECRAFT SERVICES ONLINE" : st == Status.OFFLINE ? "MINECRAFT SERVICES OFFLINE" : "CHECKING SERVICES";
		int dot = st == Status.ONLINE ? 0xFF3DDC84 : st == Status.OFFLINE ? 0xFFFF5A5A : 0xFFFFC857;
		float lw = Ui.spacedWidth(label, Fonts.BODY, 0.9f);
		float rx = width - 12 - lw;
		Ui.textSpaced(g, label, rx, by - 14.5f, 0xFFFFFFFF, Fonts.BODY, 0.9f);
		float pulse = status == Status.CHECKING ? 0.5f + 0.5f * (float) Math.sin(t * 5) : 1f;
		Ui.glow(g, rx - 8, by - 14.5f, 6, 6, Theme.alpha(dot, 0.45f * pulse));
		Ui.circle(g, rx - 8, by - 14.5f, 2.6f, Theme.alpha(dot, pulse));
		Ui.textRight(g, "Not an official Minecraft product. Not associated with Mojang or Microsoft.", width - 12, by - 4.5f, 0xCC9DB4D6, Fonts.SMALL);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button != LEFT) return false;
		if (account.mouseClicked(mx, my, width)) return true;
		float x = width / 2f - BTN_W / 2f, y = buttonsY();
		for (Action b : buttons) {
			if (Ui.inside(mx, my, x, y, BTN_W, BTN_H)) return run(b);
			y += BTN_H + BTN_GAP;
		}
		float cy = roundY() + ROUND / 2f;
		float x0 = width / 2f - ROUND_STEP * (shortcuts.size() - 1) / 2f;
		for (int i = 0; i < shortcuts.size(); i++) {
			float cx = x0 + i * ROUND_STEP;
			if (Ui.inside(mx, my, cx - ROUND / 2f, cy - ROUND / 2f, ROUND, ROUND + 14)) return run(shortcuts.get(i));
		}
		return false;
	}

	private boolean run(Action action) {
		p().playClickSound();
		action.run.run();
		return true;
	}
}
