package dev.polarclient.gui;

import com.google.gson.JsonObject;
import dev.polarclient.Polar;
import dev.polarclient.config.ClientData;
import dev.polarclient.config.ConfigManager;
import dev.polarclient.gfx.Gfx;
import dev.polarclient.gfx.UiFont;
import dev.polarclient.gui.ui.Fonts;
import dev.polarclient.gui.ui.Icon;
import dev.polarclient.gui.ui.Icons;
import dev.polarclient.gui.ui.TextField;
import dev.polarclient.gui.ui.Theme;
import dev.polarclient.gui.ui.Ui;
import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.util.Draw;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Right Shift menu: a frosted-glass window with a sidebar (Mods, HUD Editor, Settings, Cosmetics, Profiles), an
 * aurora banner over the page header, category chips and a grid of mod cards with switches.
 * <p>
 * It is laid out in its own units (a 560 x 292 window) and drawn at a whole number of screen pixels per unit picked
 * from the window size, so it keeps the same proportions at every GUI scale.
 */
public class PolarMenuView extends View {
	private enum Page { MODS, HUD_EDITOR, SETTINGS, COSMETICS, PROFILES }

	private static final String[] NAV_LABELS = {"Mods", "HUD Editor", "Settings", "Cosmetics", "Profiles"};
	private static final String[] NAV_ICONS = {Icon.MODS, Icon.GRID, Icon.SETTINGS, Icon.CHECKROOM, Icon.STACKS};

	static final float DW = 560, DH = 292, R = 11;
	private static final float SIDE_W = 100, SIDE_MIN = 40;
	private static final float NAV_X = 9, NAV_Y = 58, NAV_H = 24, NAV_STEP = 29;
	private static final float BANNER_H = 57.5f;
	private static final float PAD = 10.8f;
	private static final float CHIP_Y = 65.5f, CHIP_H = 16;
	private static final float GRID_Y = 90, CARD_H = 60.6f, CARD_GAP_X = 8, CARD_GAP_Y = 5.8f;
	private static final float SW_W = 19, SW_H = 10.8f;

	private static final String BANNER = "textures/gui/menu/banner.png";
	private static final String PEAKS = "textures/gui/menu/side_peaks.png";

	private static Page lastPage = Page.MODS;
	private static boolean collapsed;
	private static boolean onlyEnabled;
	private static Category lastFilter;
	private static boolean lastFavorites;

	private Page page = lastPage;
	private Category filter = lastFilter;
	private boolean favoritesOnly = lastFavorites;
	private TextField search;
	private TextField profileName;
	private SettingsView moduleSettings;
	private SettingsView clientSettings;
	private Module pendingModule;
	private String toast;
	private long toastAt;
	private ClientData.Profile confirmDelete;

	// layout (GUI units): window origin and units -> GUI scale
	private float ox, oy, zoom = 1;
	private float side = SIDE_W;
	private float scroll, scrollTarget, chipScroll, chipScrollTarget, chipContentW;
	private long openedAt, pageChangedAt;
	private int pageDirection = 1;
	private final Map<Module, Float> hoverAnim = new HashMap<Module, Float>();
	private final Map<Module, Float> onAnim = new HashMap<Module, Float>();
	private final float[] navHover = new float[NAV_LABELS.length];
	private Module hovered;

	public PolarMenuView(Object parent) {
		super(parent);
		if (page == Page.HUD_EDITOR) page = Page.MODS;
	}

	/** Opens straight into one module's settings (right-click in the HUD editor). */
	public PolarMenuView(Object parent, Module module) {
		this(parent);
		this.page = Page.MODS;
		this.pendingModule = module;
	}

	@Override
	protected void init() {
		openedAt = System.currentTimeMillis();
		layout();
		String previous = search != null ? search.value() : "";
		search = new TextField(32);
		search.setTextColor(0xFFFFFFFF);
		search.setValue(previous);
		search.setListener(v -> scrollTarget = 0);
		search.visible = false;
		String prevName = profileName != null ? profileName.value() : "";
		profileName = new TextField(24);
		profileName.setTextColor(0xFFFFFFFF);
		profileName.setValue(prevName);
		profileName.visible = false;

		if (moduleSettings != null) {
			Module m = moduleSettings.module();
			moduleSettings = null;
			openModule(m);
		}
		clientSettings = null;
		if (pendingModule != null) {
			openModule(pendingModule);
			pendingModule = null;
		}
	}

	/** Picks how many screen pixels one menu unit gets, then centres the window. */
	private void layout() {
		int gs = Math.max(1, p().guiScale());
		float screenW = width * gs, screenH = height * gs;
		int px = (int) Math.floor(Math.min(screenW * 0.9f / DW, screenH * 0.88f / DH));
		String size = Polar.modules().client.menuSize.get();
		if (size.equals("Small")) px -= 1;
		else if (size.equals("Large") && (px + 1) * DW <= screenW * 0.98f && (px + 1) * DH <= screenH * 0.98f) px += 1;
		if (px < 1) {
			zoom = Math.min(screenW * 0.98f / DW, screenH * 0.98f / DH) / gs; // very small window: fractional fallback
		} else {
			zoom = px / (float) gs;
		}
		ox = (width - DW * zoom) / 2f;
		oy = (height - DH * zoom) / 2f;
	}

	@Override
	public boolean pausesGame() {
		return false;
	}

	@Override
	public void onClose() {
		lastPage = page == Page.HUD_EDITOR ? Page.MODS : page;
		lastFilter = filter;
		lastFavorites = favoritesOnly;
		Polar.saveConfig();
		p().show(parent);
	}

	private float mx(double x) {
		return (float) ((x - ox) / zoom);
	}

	private float my(double y) {
		return (float) ((y - oy) / zoom);
	}

	// ------------------------------------------------------------------ geometry (menu units)

	private float contentX() {
		return side;
	}

	private float contentW() {
		return DW - side;
	}

	private int columns() {
		return contentW() > 400 ? 4 : 3;
	}

	private float cardW() {
		int cols = columns();
		return (contentW() - PAD - 7.6f - (cols - 1) * CARD_GAP_X) / cols;
	}

	private float gridX() {
		return contentX() + PAD;
	}

	private List<Module> visibleModules() {
		String q = search.value().trim().toLowerCase(Locale.ROOT);
		ClientData data = Polar.config() == null ? null : Polar.config().data();
		List<Module> out = new ArrayList<Module>();
		for (Module m : Polar.modules().all()) {
			if (m == Polar.modules().client) continue; // lives on the Settings page
			if (filter != null && m.category() != filter) continue;
			if (favoritesOnly && (data == null || !data.isFavorite(m.id()))) continue;
			if (onlyEnabled && !(m.canToggle() && m.isEnabled())) continue;
			if (!q.isEmpty() && !m.name().toLowerCase(Locale.ROOT).contains(q) && !m.description().toLowerCase(Locale.ROOT).contains(q)) continue;
			out.add(m);
		}
		return out;
	}

	private int enabledCount() {
		int n = 0;
		for (Module m : Polar.modules().all()) if (m != Polar.modules().client && m.canToggle() && m.isEnabled()) n++;
		return n;
	}

	private float gridBottom() {
		return DH - 6;
	}

	private float maxScroll() {
		int n = visibleModules().size(), cols = columns();
		int rows = (n + cols - 1) / cols;
		float content = rows * (CARD_H + CARD_GAP_Y) - CARD_GAP_Y;
		return Math.max(0, content - (gridBottom() - GRID_Y));
	}

	private void setPage(Page p) {
		if (p == Page.HUD_EDITOR) {
			lastPage = page;
			Polar.saveConfig();
			p().open(new HudEditorView(p().currentScreen()));
			return;
		}
		if (p == page && moduleSettings == null) return;
		pageDirection = p.ordinal() >= page.ordinal() ? 1 : -1;
		closeModule();
		page = p;
		scroll = scrollTarget = 0;
		confirmDelete = null;
		pageChangedAt = System.currentTimeMillis();
		if (p == Page.SETTINGS && clientSettings == null) clientSettings = new SettingsView(Polar.modules().client);
	}

	private void openModule(Module m) {
		closeModule();
		page = Page.MODS;
		moduleSettings = new SettingsView(m);
		pageDirection = 1;
		pageChangedAt = System.currentTimeMillis();
	}

	private void closeModule() {
		if (moduleSettings != null) {
			moduleSettings.dispose();
			moduleSettings = null;
			pageDirection = -1;
			pageChangedAt = System.currentTimeMillis();
		}
	}

	private void showToast(String text) {
		toast = text;
		toastAt = System.currentTimeMillis();
	}

	// ------------------------------------------------------------------ palette (the reference look, tinted by the accent)

	private static final class Pal {
		final int accent, highlight, chip, switchOn, glass, sideGlass, line, cardFill, cardBorder, text, muted, dim;

		Pal(Theme t) {
			accent = t.primary();
			highlight = Theme.mix(accent, 0xFF40E0FF, 0.45f);
			chip = Theme.mix(accent, 0xFF4FB0FF, 0.45f);
			switchOn = Theme.mix(accent, 0xFF0B5FD0, 0.78f);
			glass = Theme.alpha(Theme.mix(0xFF061328, accent, 0.03f), 0.90f);
			sideGlass = Theme.alpha(Theme.mix(0xFF030D1D, accent, 0.02f), 0.55f);
			line = Theme.alpha(Theme.mix(0xFF143259, accent, 0.10f), 0.9f);
			cardFill = Theme.alpha(Theme.mix(0xFF07172E, accent, 0.03f), 0.80f);
			cardBorder = Theme.mix(0xFF1B3B60, accent, 0.10f);
			text = 0xFFF1F6FC;
			muted = 0xFFA8B7CB;
			dim = 0xFF6F819A;
		}
	}

	// ------------------------------------------------------------------ rendering

	@Override
	public void renderBackground(Gfx g, int mouseX, int mouseY) {
		if (!p().inWorld()) MenuBackground.INSTANCE.render(g, width, height, mouseX, mouseY);
		if (Polar.modules().client.menuBlur.on()) g.blurBackground();
		g.fill(0, 0, width, height, 0x66020814);
	}

	@Override
	public void render(Gfx g, int mouseX, int mouseY, float a) {
		Ui.frame();
		layout();
		Theme t = Theme.current();
		Pal c = new Pal(t);
		float mx = mx(mouseX), my = my(mouseY);
		side = Ui.approach(side, collapsed ? SIDE_MIN : SIDE_W, 16f);
		float open = Ui.easeOut((System.currentTimeMillis() - openedAt) / 220f);

		g.push();
		g.translate(ox, oy);
		g.scale(zoom);
		Ui.setZoom(zoom);
		try {
			float s = 0.95f + 0.05f * open;
			g.translate(DW / 2f, DH / 2f);
			g.scale(s);
			g.translate(-DW / 2f, -DH / 2f);
			drawWindow(g, c);
			drawSidebar(g, mx, my, c, t);

			float slide = (1f - Ui.easeOut((System.currentTimeMillis() - pageChangedAt) / 260f)) * 14f * pageDirection;
			g.scissor(Math.round(contentX()) + 1, 1, Math.round(DW) - 1, Math.round(DH) - 1);
			drawBanner(g, c);
			g.push();
			g.translate(slide, 0);
			search.visible = false;
			profileName.visible = false;
			if (moduleSettings != null) drawModuleSettings(g, mx, my, c, t);
			else if (page == Page.MODS) drawMods(g, mx, my, c, t);
			else if (page == Page.SETTINGS) drawClientSettings(g, mx, my, c, t);
			else if (page == Page.COSMETICS) drawCosmetics(g, mx, my, c, t);
			else if (page == Page.PROFILES) drawProfiles(g, mx, my, c, t);
			g.pop();
			g.endScissor();
			drawToast(g, c);
		} finally {
			Ui.setZoom(1f);
			g.pop();
		}
	}

	private void drawWindow(Gfx g, Pal c) {
		// soft outer glow + bright rim, then the frosted glass body
		Ui.rect(g, -2.5f, -2.5f, DW + 5, DH + 5, R + 2.5f, Theme.alpha(c.highlight, 0.06f));
		Ui.rect(g, -1.2f, -1.2f, DW + 2.4f, DH + 2.4f, R + 1.2f, Theme.alpha(c.highlight, 0.14f));
		Ui.rect(g, 0, 0, DW, DH, R, c.glass);
		Ui.ring(g, 0, 0, DW, DH, R, Theme.alpha(Theme.mix(0xFF1E5B95, c.highlight, 0.35f), 0.95f), 1.5f);
		// brighter rim along the top right, like light catching the glass
		Ui.hGradient(g, DW * 0.55f, 0, DW * 0.45f - R, 0.6f, 0x0040C8FF, Theme.alpha(c.highlight, 0.85f));
	}

	private void drawSidebar(Gfx g, float mx, float my, Pal c, Theme t) {
		float w = side;
		Ui.rect(g, 1, 1, w - 1, DH - 2, R - 1, 0, 0, R - 1, c.sideGlass);
		Ui.vline(g, w, 1, DH - 2, 1, c.line);
		float expand = (side - SIDE_MIN) / (SIDE_W - SIDE_MIN);

		// mountains along the bottom (the bottom band is drawn as a rounded block so the corner stays round)
		float peaksH = 52;
		g.scissor(1, Math.round(DH - R - peaksH - 1), Math.round(w), Math.round(DH - R) + 1);
		g.blit(PEAKS, 0, DH - R - peaksH + 0.5f, w, peaksH, 0, 0, Math.min(1, w / SIDE_W), 1, 0xFFFFFFFF);
		g.endScissor();
		Ui.rect(g, 1, DH - R - 0.5f, w - 1, R - 0.5f, 0, 0, 0, R - 1, 0xFF131F39);

		// logo: badge + POLAR / CLIENT
		float bx = 26 - (1 - expand) * 6, by = 25.5f;
		Ui.glow(g, bx, by, 24, 24, Theme.alpha(c.highlight, 0.30f));
		Ui.circle(g, bx, by, 15.5f, Theme.alpha(c.highlight, 0.9f));
		Ui.circle(g, bx, by, 14.6f, 0xFF0A1830);
		Draw.texture(g, Draw.MARK_LARGE, Math.round(bx - 13), Math.round(by - 13), 26, 26);
		if (expand > 0.05f) {
			int ta = Math.round(255 * expand);
			g.push();
			g.translate(45, 16.5f);
			g.scale(0.86f);
			Ui.text(g, "POLAR", 0, 0, Draw.alpha(c.text, ta), Fonts.TITLE);
			g.pop();
			Ui.textSpaced(g, "CLIENT", 45.5f, 33.5f, Draw.alpha(0xFF8FB4E6, ta), Fonts.BODY, 0.7f);
		}

		// navigation
		Page active = moduleSettings != null ? Page.MODS : page;
		for (int i = 0; i < NAV_LABELS.length; i++) {
			float iy = NAV_Y + i * NAV_STEP;
			float iw = w - NAV_X * 2;
			boolean on = Page.values()[i] == active;
			boolean hover = Ui.inside(mx, my, NAV_X, iy, iw, NAV_H);
			navHover[i] = Ui.approach(navHover[i], hover && !on ? 1f : 0f, 18f);
			if (on) {
				Ui.glow(g, NAV_X + 2, iy + NAV_H / 2f, 16, 16, Theme.alpha(c.highlight, 0.25f));
				Ui.rect(g, NAV_X, iy, iw, NAV_H, 5.5f, Theme.alpha(Theme.mix(0xFF2C5A8C, c.highlight, 0.25f), 0.75f));
				Ui.rect(g, NAV_X + 0.5f, iy + 0.5f, iw - 1, NAV_H - 1, 5f, 0xF0102238);
				Ui.rect(g, NAV_X, iy + 3, 1.2f, NAV_H - 6, 0.6f, c.highlight);
			} else if (navHover[i] > 0.01f) {
				Ui.rect(g, NAV_X, iy, iw, NAV_H, 5.5f, Theme.alpha(0xFF1A2E4A, 0.6f * navHover[i]));
			}
			float iconX = NAV_X + 15.7f - (1 - expand) * (15.7f - iw / 2f);
			Ui.icon(g, NAV_ICONS[i], iconX, iy + NAV_H / 2f, on ? c.text : 0xFFD2DDEA, Fonts.ICON_M);
			if (expand > 0.3f) {
				int la = Math.round(255 * Math.min(1, (expand - 0.3f) / 0.7f));
				Ui.textMid(g, NAV_LABELS[i], NAV_X + 31.7f, iy + NAV_H / 2f, Draw.alpha(on ? c.text : 0xFFD2DDEA, la), Fonts.BODY);
			}
		}

		// collapse / expand
		float cx = collapsed ? w / 2f : 19, cy = DH - 18;
		boolean ch = Ui.inside(mx, my, cx - 8, cy - 8, 16, 16);
		if (ch) Ui.circle(g, cx, cy, 8, Theme.alpha(0xFF1A2E4A, 0.8f));
		Ui.icon(g, collapsed ? Icon.CHEVRON_RIGHT : Icon.CHEVRON_LEFT, cx, cy, ch ? c.text : 0xFFC4D2E4, Fonts.ICON_M);
	}

	private void drawBanner(Gfx g, Pal c) {
		float x = contentX(), w = DW - x;
		// the texture is 8:1; keep it anchored to the right so the moon and peaks stay put when the sidebar folds
		float tw = BANNER_H * 8f;
		float u0 = Math.max(0, 1 - w / tw);
		g.blit(BANNER, x, 1, w, BANNER_H - 1, u0, 0, 1, 1, 0xFFFFFFFF);
		Ui.hline(g, x, BANNER_H, w, 1, c.line);
	}

	private void drawHeader(Gfx g, String title, String subtitle, Pal c) {
		Ui.text(g, title, contentX() + 14, 20, c.text, Fonts.TITLE);
		if (subtitle != null) Ui.text(g, subtitle, contentX() + 14, 37, c.muted, Fonts.SMALL);
	}

	private void drawSearch(Gfx g, float mx, float my, Pal c) {
		float sw = 117, sh = 18.7f, sx = DW - 13 - sw, sy = 22.3f;
		boolean focused = search.focused();
		boolean hover = Ui.inside(mx, my, sx, sy, sw, sh);
		Ui.rect(g, sx, sy, sw, sh, sh / 2f, focused ? c.highlight : hover ? Theme.mix(c.cardBorder, c.highlight, 0.3f) : c.cardBorder);
		Ui.rect(g, sx + 0.6f, sy + 0.6f, sw - 1.2f, sh - 1.2f, sh / 2f - 0.6f, 0xEE061226);
		Ui.icon(g, Icon.SEARCH, sx + 11, sy + sh / 2f, focused ? c.highlight : 0xFFB5C4D6, Fonts.ICON_M);
		if (search.value().isEmpty() && !focused) Ui.textMid(g, "Search mods...", sx + 20, sy + sh / 2f, 0xFFA8B8CC, Fonts.BODY);
		search.visible = true;
		search.setBounds(Math.round(sx + 20), Math.round(sy + 5.5f), Math.round(sw - 28));
		search.render(g);
	}

	private void drawSwitch(Gfx g, float x, float y, float onF, boolean hover, Pal c) {
		int track = Theme.lerp(0xFF222C3D, c.switchOn, onF);
		if (onF > 0.5f) Ui.glow(g, x + SW_W / 2f, y + SW_H / 2f, SW_W * 0.9f, SW_H * 1.2f, Theme.alpha(c.switchOn, 0.35f * onF));
		Ui.rect(g, x, y, SW_W, SW_H, SW_H / 2f, Theme.lerp(0xFF3A4760, Theme.mix(c.switchOn, 0xFFFFFFFF, 0.15f), onF));
		Ui.rect(g, x + 0.6f, y + 0.6f, SW_W - 1.2f, SW_H - 1.2f, SW_H / 2f - 0.6f, hover ? Theme.mix(track, 0xFFFFFFFF, 0.06f) : track);
		float kr = SW_H / 2f - 1.6f;
		float kx = x + SW_H / 2f + (SW_W - SW_H) * onF;
		Ui.circle(g, kx, y + SW_H / 2f, kr, Theme.lerp(0xFF7C8AA3, 0xFFF0F5FB, onF));
	}

	// ------------------------------------------------------------------ Mods page

	private void drawMods(Gfx g, float mx, float my, Pal c, Theme t) {
		drawHeader(g, "Mods", "Customize your gameplay with built-in mods.", c);
		drawSearch(g, mx, my, c);
		drawChips(g, mx, my, c);

		// "Enabled (n)" switch: shows only the mods that are on
		float swX = DW - 13 - SW_W, swY = CHIP_Y + (CHIP_H - SW_H) / 2f;
		String label = "Enabled (" + enabledCount() + ")";
		Ui.textRight(g, label, swX - 5, CHIP_Y + CHIP_H / 2f, 0xFFD5DFEB, Fonts.BODY);
		drawSwitch(g, swX, swY, onlyEnabled ? 1 : 0, Ui.inside(mx, my, swX, swY, SW_W, SW_H), c);

		scroll = Ui.approach(scroll, scrollTarget, 18f);
		float top = GRID_Y, bottom = gridBottom();
		boolean mouseInGrid = Ui.inside(mx, my, contentX(), top - 3, contentW(), bottom - top + 3);
		g.scissor(Math.round(contentX()) + 1, Math.round(top - 3), Math.round(DW) - 1, Math.round(gridBottom()) + 2);
		List<Module> mods = visibleModules();
		int cols = columns();
		float cw = cardW(), sx = gridX();
		hovered = null;
		if (mods.isEmpty()) {
			String msg = onlyEnabled ? "No mods are switched on here" : favoritesOnly ? "Star a mod to pin it here" : "No mods match your search";
			Ui.textCentered(g, msg, contentX() + contentW() / 2f, top + 30, c.muted, Fonts.BODY);
		}
		for (int i = 0; i < mods.size(); i++) {
			Module m = mods.get(i);
			float x = sx + (i % cols) * (cw + CARD_GAP_X);
			float y = top + (i / cols) * (CARD_H + CARD_GAP_Y) - scroll;
			if (y + CARD_H < top - 4 || y > bottom + 6) continue;
			boolean hover = mouseInGrid && Ui.inside(mx, my, x, y, cw, CARD_H);
			if (hover) hovered = m;
			drawCard(g, m, x, y, cw, hover, mx, my, c);
		}
		g.endScissor();
		float max = maxScroll();
		if (max > 0) {
			float h = bottom - top, thumb = Math.max(16, h * h / (h + max));
			Ui.rect(g, DW - 4.2f, top + (h - thumb) * (scroll / max), 1.6f, thumb, 0.8f, Theme.alpha(0xFF9FB8D8, 0.35f));
		}
	}

	private static final class Chip {
		final String label, icon;
		final Category category;
		final boolean favorites;

		Chip(String label, String icon, Category category, boolean favorites) {
			this.label = label;
			this.icon = icon;
			this.category = category;
			this.favorites = favorites;
		}
	}

	private List<Chip> chips() {
		List<Chip> out = new ArrayList<Chip>();
		out.add(new Chip("All", null, null, false));
		out.add(new Chip("Favorites", Icon.STAR, null, true));
		for (Category cat : Category.values()) {
			boolean any = false;
			for (Module m : Polar.modules().all()) if (m.category() == cat && m != Polar.modules().client) any = true;
			if (any) out.add(new Chip(cat.label, null, cat, false));
		}
		return out;
	}

	private boolean selected(Chip chip) {
		return chip.favorites ? favoritesOnly : !favoritesOnly && filter == chip.category;
	}

	private float chipW(Chip chip) {
		float w = Fonts.width(chip.label, Fonts.BODY) + 20;
		if (selected(chip) || chip.icon != null) w += 9;
		return Math.max(w, 34);
	}

	private float chipsRight() {
		return DW - 13 - SW_W - 5 - Fonts.width("Enabled (" + enabledCount() + ")", Fonts.BODY) - 10;
	}

	private void drawChips(Gfx g, float mx, float my, Pal c) {
		float left = gridX(), right = chipsRight();
		List<Chip> chips = chips();
		float total = 0;
		for (Chip chip : chips) total += chipW(chip) + 4;
		chipContentW = total - 4;
		float maxS = Math.max(0, chipContentW - (right - left));
		chipScrollTarget = Math.max(0, Math.min(maxS, chipScrollTarget));
		chipScroll = Ui.approach(chipScroll, chipScrollTarget, 18f);
		g.scissor(Math.round(left) - 4, Math.round(CHIP_Y - 4), Math.round(right), Math.round(CHIP_Y + CHIP_H + 4));
		float x = left - chipScroll;
		for (Chip chip : chips) {
			float w = chipW(chip);
			boolean sel = selected(chip);
			boolean hover = Ui.inside(mx, my, x, CHIP_Y, w, CHIP_H) && mx < right;
			float mid = CHIP_Y + CHIP_H / 2f;
			if (sel) {
				Ui.glow(g, x + w / 2f, mid, w * 0.7f, CHIP_H, Theme.alpha(c.chip, 0.30f));
				Ui.gradientRect(g, x, CHIP_Y, w, CHIP_H, CHIP_H / 2f, Theme.mix(c.chip, 0xFFFFFFFF, 0.12f), Theme.mix(c.chip, 0xFF2A7BD8, 0.25f));
				Ui.icon(g, chip.icon != null ? chip.icon : Icon.CHECK, x + 11, mid, 0xFFFFFFFF, chip.icon != null ? Fonts.ICON_M_FILL : Fonts.ICON_M);
				Ui.textMid(g, chip.label, x + 19, mid, 0xFFFFFFFF, Fonts.BODY);
			} else {
				Ui.rect(g, x, CHIP_Y, w, CHIP_H, CHIP_H / 2f, hover ? Theme.mix(c.cardBorder, c.highlight, 0.35f) : 0xFF162A44);
				Ui.rect(g, x + 0.6f, CHIP_Y + 0.6f, w - 1.2f, CHIP_H - 1.2f, CHIP_H / 2f - 0.6f, hover ? 0xFF13243C : 0xFF0D1C32);
				float tx = x + 10;
				if (chip.icon != null) {
					Ui.icon(g, chip.icon, x + 11, mid, 0xFFFFD36B, Fonts.ICON_M);
					tx = x + 19;
				}
				Ui.textMid(g, chip.label, tx, mid, 0xFFD3DEEA, Fonts.BODY);
			}
			x += w + 4;
		}
		g.endScissor();
		if (maxS > 0) {
			// fade + arrow buttons where the chips continue
			float mid = CHIP_Y + CHIP_H / 2f;
			if (chipScroll < maxS - 0.5f) {
				Ui.hGradient(g, right - 34, CHIP_Y - 1, 20, CHIP_H + 2, 0x00071429, 0xFF071429);
				Ui.rect(g, right - 14, CHIP_Y - 1, 14, CHIP_H + 2, 0, 0xFF071429);
				Ui.circle(g, right - 7, mid, 7, 0xFF13243C);
				Ui.icon(g, Icon.CHEVRON_RIGHT, right - 7, mid, 0xFFE2EAF4, Fonts.ICON_M);
			}
			if (chipScroll > 0.5f) {
				Ui.rect(g, left - 4, CHIP_Y - 1, 18, CHIP_H + 2, 0, 0xFF071429);
				Ui.hGradient(g, left + 14, CHIP_Y - 1, 20, CHIP_H + 2, 0xFF071429, 0x00071429);
				Ui.circle(g, left + 5, mid, 7, 0xFF13243C);
				Ui.icon(g, Icon.CHEVRON_LEFT, left + 5, mid, 0xFFE2EAF4, Fonts.ICON_M);
			}
		}
	}

	private void drawCard(Gfx g, Module m, float x, float y, float w, boolean hover, float mx, float my, Pal c) {
		Float prevHover = hoverAnim.get(m);
		float hv = Ui.approach(prevHover == null ? 0f : prevHover, hover ? 1f : 0f, 16f);
		hoverAnim.put(m, hv);
		boolean on = m.canToggle() && m.isEnabled();
		Float prevOn = onAnim.get(m);
		float onF = Ui.approach(prevOn == null ? (on ? 1f : 0f) : prevOn, on ? 1f : 0f, 14f);
		onAnim.put(m, onF);
		String why = m.unavailableReason();
		ClientData data = Polar.config() == null ? null : Polar.config().data();
		boolean fav = data != null && data.isFavorite(m.id());

		if (hv > 0.02f) {
			Ui.glow(g, x + w / 2f, y + CARD_H / 2f, w * 0.62f, CARD_H * 0.75f, Theme.alpha(c.highlight, 0.16f * hv));
		}
		int border = Theme.lerp(c.cardBorder, c.highlight, hv);
		Ui.rect(g, x, y, w, CARD_H, 6.4f, border);
		float bt = hv > 0.5f ? 1.3f : 0.65f;
		Ui.rect(g, x + bt, y + bt, w - 2 * bt, CARD_H - 2 * bt, 6.4f - bt, Theme.lerp(0xF0081931, 0xF00A1E3A, hv));

		int fg = why != null ? c.dim : c.text;
		g.push();
		g.translate(x + 16.7f, y + 15.1f);
		g.scale(0.68f);
		Ui.icon(g, Icons.of(m), 0, 0, fg, Fonts.ICON_L);
		g.pop();

		// gear (settings) and star (favourite)
		float gx = x + w - 11.5f, gy = y + 10.4f;
		boolean gearHover = hover && Ui.inside(mx, my, gx - 6, gy - 6, 12, 12);
		Ui.icon(g, Icon.SETTINGS, gx, gy, gearHover ? c.text : 0xFFA4B3C7, Fonts.ICON_S);
		float stX = gx - 12;
		boolean starHover = hover && Ui.inside(mx, my, stX - 6, gy - 6, 12, 12);
		if (fav) {
			g.push();
			g.translate(stX, gy);
			g.scale(0.75f);
			Ui.icon(g, Icon.STAR, 0, 0, 0xFFFFD36B, Fonts.ICON_M_FILL);
			g.pop();
		} else if (hv > 0.05f) {
			Ui.icon(g, Icon.STAR, stX, gy, Theme.alpha(starHover ? 0xFFFFD36B : 0xFFA4B3C7, hv), Fonts.ICON_S);
		}

		Ui.text(g, Fonts.ellipsize(m.name(), Fonts.BOLD, Math.round(w - 14)), x + 7.6f, y + 30.3f, fg, Fonts.BOLD);
		String desc = why != null ? why : m.description();
		List<String> lines = wrap(desc, Fonts.SMALL, w - 7.6f - SW_W - 10, 2);
		for (int i = 0; i < lines.size(); i++) {
			Ui.text(g, lines.get(i), x + 7.6f, y + 40.7f + i * 7.9f, why != null ? c.dim : c.muted, Fonts.SMALL);
		}
		if (m.canToggle() && why == null) {
			float swx = x + w - 7 - SW_W, swy = y + 43;
			drawSwitch(g, swx, swy, onF, hover && Ui.inside(mx, my, swx, swy, SW_W, SW_H), c);
		}
	}

	/** Word-wraps text to at most {@code maxLines} lines (the last one gets "..." if it doesn't fit). */
	static List<String> wrap(String text, UiFont font, float maxW, int maxLines) {
		List<String> out = new ArrayList<String>();
		String[] words = text.split(" ");
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < words.length; i++) {
			String next = line.length() == 0 ? words[i] : line + " " + words[i];
			if (Fonts.width(next, font) <= maxW || line.length() == 0) {
				line = new StringBuilder(next);
			} else {
				out.add(line.toString());
				line = new StringBuilder(words[i]);
				if (out.size() == maxLines - 1) {
					StringBuilder rest = new StringBuilder(line);
					for (int j = i + 1; j < words.length; j++) rest.append(' ').append(words[j]);
					out.add(Fonts.ellipsize(rest.toString(), font, (int) maxW));
					return out;
				}
			}
		}
		if (line.length() > 0) out.add(Fonts.ellipsize(line.toString(), font, (int) maxW));
		return out;
	}

	// ------------------------------------------------------------------ settings pages

	private void drawModuleSettings(Gfx g, float mx, float my, Pal c, Theme t) {
		Module m = moduleSettings.module();
		float x0 = contentX() + 14;
		boolean backHover = Ui.inside(mx, my, x0 - 3, 19, 16, 16);
		if (backHover) Ui.circle(g, x0 + 5, 27, 8, Theme.alpha(0xFF1A2E4A, 0.8f));
		Ui.icon(g, Icon.BACK, x0 + 5, 27, c.text, Fonts.ICON_M);
		Ui.text(g, m.name(), x0 + 16, 20, c.text, Fonts.TITLE);
		String why = m.unavailableReason();
		Ui.text(g, why != null ? why : m.description(), x0 + 16, 37, why != null ? c.dim : c.muted, Fonts.SMALL);

		float right = DW - 13;
		boolean resetHover = Ui.inside(mx, my, right - 16, 19, 16, 16);
		Ui.rect(g, right - 16, 19, 16, 16, 8, resetHover ? 0xFF1C3352 : 0xCC0D1C32);
		Ui.icon(g, Icon.RESET, right - 8, 27, c.text, Fonts.ICON_M);
		ClientData data = Polar.config() == null ? null : Polar.config().data();
		boolean fav = data != null && data.isFavorite(m.id());
		boolean starHover = Ui.inside(mx, my, right - 36, 19, 16, 16);
		Ui.rect(g, right - 36, 19, 16, 16, 8, starHover ? 0xFF1C3352 : 0xCC0D1C32);
		Ui.icon(g, Icon.STAR, right - 28, 27, fav ? 0xFFFFD36B : c.text, fav ? Fonts.ICON_M_FILL : Fonts.ICON_M);
		if (m.canToggle() && why == null) {
			drawSwitch(g, right - 42 - SW_W, 27 - SW_H / 2f, m.isEnabled() ? 1 : 0, Ui.inside(mx, my, right - 42 - SW_W, 27 - SW_H / 2f, SW_W, SW_H), c);
		}
		if (m.settings().isEmpty()) {
			Ui.textCentered(g, "This mod has no settings.", contentX() + contentW() / 2f, 110, c.muted, Fonts.BODY);
			return;
		}
		moduleSettings.render(g, contentX() + 14, BANNER_H + 8, contentW() - 28, DH - BANNER_H - 14, Math.round(mx), Math.round(my), t);
	}

	private void drawClientSettings(Gfx g, float mx, float my, Pal c, Theme t) {
		drawHeader(g, "Settings", "Theme, menus and how Polar Client looks.", c);
		if (clientSettings == null) clientSettings = new SettingsView(Polar.modules().client);
		clientSettings.render(g, contentX() + 14, BANNER_H + 8, contentW() - 28, DH - BANNER_H - 14, Math.round(mx), Math.round(my), t);
	}

	// ------------------------------------------------------------------ Cosmetics

	private static float buttonW(String label, String icon) {
		return label.isEmpty() ? 22 : Fonts.width(label, Fonts.BODY) + (icon != null ? 30 : 20);
	}

	private float[] button(Gfx g, String label, String icon, float x, float y, float mx, float my, Pal c, boolean primary) {
		float w = buttonW(label, icon), h = 17;
		boolean hover = Ui.inside(mx, my, x, y, w, h);
		if (primary) {
			Ui.gradientRect(g, x, y, w, h, h / 2f, Theme.mix(c.chip, 0xFFFFFFFF, hover ? 0.22f : 0.12f), Theme.mix(c.chip, 0xFF2A7BD8, 0.25f));
		} else {
			Ui.rect(g, x, y, w, h, h / 2f, hover ? Theme.mix(c.cardBorder, c.highlight, 0.4f) : 0xFF1A3150);
			Ui.rect(g, x + 0.6f, y + 0.6f, w - 1.2f, h - 1.2f, h / 2f - 0.6f, hover ? 0xFF13243C : 0xFF0D1C32);
		}
		float tx = x + 10;
		if (icon != null) {
			Ui.icon(g, icon, label.isEmpty() ? x + w / 2f : x + 12, y + h / 2f, 0xFFFFFFFF, Fonts.ICON_M);
			tx = x + 20;
		}
		if (!label.isEmpty()) Ui.textMid(g, label, tx, y + h / 2f, 0xFFFFFFFF, Fonts.BODY);
		return new float[]{x, y, w, h};
	}

	private float[] skinButton, profileSaveButton, exportButton, importButton;

	private void drawCosmetics(Gfx g, float mx, float my, Pal c, Theme t) {
		drawHeader(g, "Cosmetics", "Your skin, cape and how other players see you.", c);
		float x = gridX(), y = GRID_Y - 18, w = contentW() - PAD - 7.6f, h = 82;
		Ui.rect(g, x, y, w, h, 6.4f, c.cardBorder);
		Ui.rect(g, x + 0.65f, y + 0.65f, w - 1.3f, h - 1.3f, 5.8f, 0xF0081931);
		float head = 46;
		Ui.glow(g, x + 14 + head / 2f, y + 18 + head / 2f, head * 0.8f, head * 0.8f, Theme.alpha(c.highlight, 0.18f));
		g.playerHead(p().userId(), p().userName(), x + 14, y + 18, head);
		String name = p().userName();
		Ui.text(g, name, x + 74, y + 22, c.text, Fonts.TITLE);
		Ui.text(g, "Signed in on Minecraft " + p().minecraftVersion(), x + 74, y + 39, c.muted, Fonts.SMALL);
		skinButton = button(g, "Skin layers", Icon.CHECKROOM, x + 74, y + 51, mx, my, c, true);

		float ny = y + h + 8;
		Ui.rect(g, x, ny, w, 46, 6.4f, c.cardBorder);
		Ui.rect(g, x + 0.65f, ny + 0.65f, w - 1.3f, 46 - 1.3f, 5.8f, 0xF0081931);
		Ui.icon(g, Icon.AUTO_AWESOME, x + 16, ny + 15, c.highlight, Fonts.ICON_M);
		Ui.text(g, "Skins and capes", x + 28, ny + 12.5f, c.text, Fonts.BOLD);
		Ui.text(g, "Upload a new skin or pick one of your capes on the Cosmetics page of the Polar launcher.", x + 28, ny + 24, c.muted, Fonts.SMALL);
		Ui.text(g, "Changes show up for everyone the next time you join a server.", x + 28, ny + 32.5f, c.muted, Fonts.SMALL);
	}

	// ------------------------------------------------------------------ Profiles

	private final List<float[]> profileRows = new ArrayList<float[]>();

	private void drawProfiles(Gfx g, float mx, float my, Pal c, Theme t) {
		drawHeader(g, "Profiles", "Save which mods are on, their settings and your HUD layout.", c);
		ClientData data = Polar.config().data();
		float x = gridX(), w = contentW() - PAD - 7.6f;
		// new profile: name box + Save, then Export / Import on the right
		float y = CHIP_Y;
		float fw = 120, fh = 17;
		boolean focused = profileName.focused();
		Ui.rect(g, x, y, fw, fh, fh / 2f, focused ? c.highlight : c.cardBorder);
		Ui.rect(g, x + 0.6f, y + 0.6f, fw - 1.2f, fh - 1.2f, fh / 2f - 0.6f, 0xEE061226);
		if (profileName.value().isEmpty() && !focused) Ui.textMid(g, "New profile name", x + 9, y + fh / 2f, 0xFF7F92AB, Fonts.BODY);
		profileName.visible = true;
		profileName.setBounds(Math.round(x + 9), Math.round(y + 4.5f), Math.round(fw - 16));
		profileName.render(g);
		profileSaveButton = button(g, "Save current", Icon.SAVE, x + fw + 5, y, mx, my, c, true);
		float ix = x + w - buttonW("Import", Icon.PASTE);
		importButton = button(g, "Import", Icon.PASTE, ix, y, mx, my, c, false);
		ix -= buttonW("Export", Icon.COPY) + 5;
		exportButton = button(g, "Export", Icon.COPY, ix, y, mx, my, c, false);

		profileRows.clear();
		float ry = GRID_Y - 2 - scroll;
		scroll = Ui.approach(scroll, scrollTarget, 18f);
		g.scissor(Math.round(contentX()) + 1, Math.round(GRID_Y - 4), Math.round(DW) - 1, Math.round(DH) - 2);
		if (data.profiles().isEmpty()) {
			Ui.textCentered(g, "No profiles yet", contentX() + contentW() / 2f, GRID_Y + 30, c.text, Fonts.BOLD);
			Ui.textCentered(g, "Type a name and press Save current to keep your setup.", contentX() + contentW() / 2f, GRID_Y + 44, c.muted, Fonts.SMALL);
		}
		SimpleDateFormat fmt = new SimpleDateFormat("d MMM, HH:mm", Locale.ROOT);
		for (ClientData.Profile pr : data.profiles()) {
			float rh = 30;
			boolean active = ClientData.sameName(pr.name, data.activeProfile());
			boolean hover = Ui.inside(mx, my, x, ry, w, rh);
			Ui.rect(g, x, ry, w, rh, 6, active ? Theme.mix(c.cardBorder, c.highlight, 0.6f) : hover ? Theme.mix(c.cardBorder, c.highlight, 0.3f) : c.cardBorder);
			Ui.rect(g, x + 0.65f, ry + 0.65f, w - 1.3f, rh - 1.3f, 5.4f, active ? 0xF00B2242 : 0xF0081931);
			Ui.icon(g, Icon.STACKS, x + 14, ry + rh / 2f, active ? c.highlight : 0xFFB5C4D6, Fonts.ICON_M);
			Ui.text(g, pr.name, x + 26, ry + 8, c.text, Fonts.BOLD);
			String sub = pr.enabledCount() + " mods on" + (pr.savedAt > 0 ? "  ·  saved " + fmt.format(new Date(pr.savedAt)) : "");
			Ui.text(g, sub, x + 26, ry + 18.5f, c.muted, Fonts.SMALL);
			if (active) {
				float bw = Fonts.width("Active", Fonts.SMALL) + 10, bx = x + 30 + Fonts.width(pr.name, Fonts.BOLD);
				Ui.rect(g, bx, ry + 7, bw, 9, 4.5f, Theme.alpha(c.chip, 0.25f));
				Ui.textMid(g, "Active", bx + 5, ry + 11.5f, c.highlight, Fonts.SMALL);
			}
			// Load / Update / Delete
			boolean confirming = confirmDelete == pr;
			String delLabel = confirming ? "Delete?" : "";
			String delIcon = confirming ? null : Icon.DELETE;
			float bx = x + w - 8 - buttonW(delLabel, delIcon);
			float[] del = button(g, delLabel, delIcon, bx, ry + 6.5f, mx, my, c, false);
			bx -= buttonW("Update", null) + 4;
			float[] upd = button(g, "Update", null, bx, ry + 6.5f, mx, my, c, false);
			bx -= buttonW("Load", null) + 4;
			float[] load = button(g, "Load", null, bx, ry + 6.5f, mx, my, c, true);
			profileRows.add(new float[]{load[0], load[1], load[2], load[3], upd[0], upd[2], del[0], del[2]});
			ry += rh + 5;
		}
		g.endScissor();
	}

	private float profilesMaxScroll() {
		int n = Polar.config().data().profiles().size();
		return Math.max(0, n * 35 - (DH - GRID_Y - 4));
	}

	private void drawToast(Gfx g, Pal c) {
		if (toast == null) return;
		long age = System.currentTimeMillis() - toastAt;
		if (age > 2600) {
			toast = null;
			return;
		}
		float a = Math.min(1, Math.min(age / 150f, (2600 - age) / 300f));
		float w = Fonts.width(toast, Fonts.BODY) + 24, h = 16, x = contentX() + (contentW() - w) / 2f, y = DH - 24;
		Ui.rect(g, x, y, w, h, h / 2f, Theme.alpha(c.highlight, 0.8f * a));
		Ui.rect(g, x + 0.6f, y + 0.6f, w - 1.2f, h - 1.2f, h / 2f - 0.6f, Theme.alpha(0xFF0B1B33, a));
		Ui.textCentered(g, toast, x + w / 2f, y + h / 2f, Theme.alpha(0xFFFFFFFF, a), Fonts.BODY);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(double rawX, double rawY, int button) {
		float mx = mx(rawX), my = my(rawY);
		if (search.visible && search.mouseClicked(mx, my)) return true;
		if (profileName.visible && profileName.mouseClicked(mx, my)) return true;
		if (!Ui.inside(mx, my, 0, 0, DW, DH)) {
			onClose();
			return true;
		}
		// sidebar
		if (mx < side) {
			for (int i = 0; i < NAV_LABELS.length; i++) {
				float iy = NAV_Y + i * NAV_STEP;
				if (Ui.inside(mx, my, NAV_X, iy, side - NAV_X * 2, NAV_H)) {
					p().playClickSound();
					setPage(Page.values()[i]);
					return true;
				}
			}
			float cx = collapsed ? side / 2f : 19, cy = DH - 18;
			if (Ui.inside(mx, my, cx - 8, cy - 8, 16, 16)) {
				collapsed = !collapsed;
				return true;
			}
			return true;
		}
		if (moduleSettings != null) return clickModuleSettings(mx, my, button);
		if (page == Page.MODS) return clickMods(mx, my, button);
		if (page == Page.SETTINGS && clientSettings != null) return clientSettings.mouseClicked(mx, my, button);
		if (page == Page.COSMETICS) return clickCosmetics(mx, my);
		if (page == Page.PROFILES) return clickProfiles(mx, my);
		return false;
	}

	private static boolean in(float[] b, float mx, float my) {
		return b != null && Ui.inside(mx, my, b[0], b[1], b[2], b[3]);
	}

	private boolean clickModuleSettings(float mx, float my, int button) {
		Module m = moduleSettings.module();
		float x0 = contentX() + 14, right = DW - 13;
		if (Ui.inside(mx, my, x0 - 3, 19, 16, 16)) {
			closeModule();
			return true;
		}
		if (Ui.inside(mx, my, right - 16, 19, 16, 16)) {
			m.resetSettings();
			moduleSettings.refreshTextBoxes();
			showToast(m.name() + " settings reset");
			return true;
		}
		if (Ui.inside(mx, my, right - 36, 19, 16, 16)) {
			Polar.config().data().toggleFavorite(m.id());
			return true;
		}
		if (m.canToggle() && m.unavailableReason() == null && Ui.inside(mx, my, right - 42 - SW_W, 27 - SW_H / 2f, SW_W, SW_H)) {
			m.toggle();
			return true;
		}
		return moduleSettings.mouseClicked(mx, my, button);
	}

	private boolean clickMods(float mx, float my, int button) {
		// Enabled switch
		float swX = DW - 13 - SW_W, swY = CHIP_Y + (CHIP_H - SW_H) / 2f;
		if (Ui.inside(mx, my, swX - Fonts.width("Enabled (" + enabledCount() + ")", Fonts.BODY) - 6, swY - 2, SW_W + 60, SW_H + 4) && mx > chipsRight()) {
			onlyEnabled = !onlyEnabled;
			scroll = scrollTarget = 0;
			return true;
		}
		// chips
		float left = gridX(), right = chipsRight();
		if (my >= CHIP_Y && my < CHIP_Y + CHIP_H && mx >= left - 4 && mx < right) {
			if (chipContentW > right - left) {
				if (mx > right - 16 && chipScroll < chipContentW - (right - left) - 0.5f) {
					chipScrollTarget += 80;
					return true;
				}
				if (mx < left + 14 && chipScroll > 0.5f) {
					chipScrollTarget -= 80;
					return true;
				}
			}
			float x = left - chipScroll;
			for (Chip chip : chips()) {
				float w = chipW(chip);
				if (mx >= x && mx < x + w) {
					if (chip.favorites) {
						favoritesOnly = !favoritesOnly;
						filter = null;
					} else {
						favoritesOnly = false;
						filter = chip.category;
					}
					scroll = scrollTarget = 0;
					return true;
				}
				x += w + 4;
			}
			return true;
		}
		// cards
		float top = GRID_Y, bottom = gridBottom();
		if (!Ui.inside(mx, my, contentX(), top - 3, contentW(), bottom - top + 3)) return false;
		List<Module> mods = visibleModules();
		int cols = columns();
		float cw = cardW(), sx = gridX();
		for (int i = 0; i < mods.size(); i++) {
			float x = sx + (i % cols) * (cw + CARD_GAP_X);
			float y = top + (i / cols) * (CARD_H + CARD_GAP_Y) - scroll;
			if (!Ui.inside(mx, my, x, y, cw, CARD_H)) continue;
			Module m = mods.get(i);
			float gx = x + cw - 11.5f, gy = y + 10.4f;
			if (Ui.inside(mx, my, gx - 12 - 6, gy - 6, 12, 12)) {
				Polar.config().data().toggleFavorite(m.id());
				showToast(Polar.config().data().isFavorite(m.id()) ? m.name() + " added to favorites" : m.name() + " removed from favorites");
				return true;
			}
			boolean gear = Ui.inside(mx, my, gx - 6, gy - 6, 12, 12);
			if (button == RIGHT || gear || !m.canToggle() || m.unavailableReason() != null) openModule(m);
			else {
				m.toggle();
				p().playClickSound();
			}
			return true;
		}
		return false;
	}

	private boolean clickCosmetics(float mx, float my) {
		if (in(skinButton, mx, my)) {
			p().openSkinOptions(p().currentScreen());
			return true;
		}
		return false;
	}

	private boolean clickProfiles(float mx, float my) {
		ConfigManager config = Polar.config();
		ClientData data = config.data();
		if (in(profileSaveButton, mx, my)) {
			String name = ClientData.cleanName(profileName.value());
			if (name.isEmpty()) name = data.freeName("Profile");
			if (data.profile(name) == null && data.profiles().size() >= ClientData.MAX_PROFILES) {
				showToast("You can keep up to " + ClientData.MAX_PROFILES + " profiles");
				return true;
			}
			data.put(name, config.snapshot());
			data.setActiveProfile(name);
			profileName.setValue("");
			profileName.setFocused(false);
			Polar.saveConfig();
			showToast("Saved " + name);
			return true;
		}
		if (in(exportButton, mx, my)) {
			String name = data.activeProfile() != null ? data.activeProfile() : "My setup";
			p().setClipboard(config.export(name));
			showToast("Copied your setup to the clipboard");
			return true;
		}
		if (in(importButton, mx, my)) {
			String text = p().clipboard();
			JsonObject mods = ConfigManager.parseExport(text);
			if (mods == null) {
				showToast("The clipboard doesn't hold a Polar profile");
				return true;
			}
			String name = ClientData.cleanName(ConfigManager.exportName(text));
			if (name.isEmpty() || data.profile(name) != null) name = data.freeName("Imported");
			if (data.profiles().size() >= ClientData.MAX_PROFILES) {
				showToast("You can keep up to " + ClientData.MAX_PROFILES + " profiles");
				return true;
			}
			config.apply(mods);
			data.put(name, config.snapshot());
			data.setActiveProfile(name);
			Polar.saveConfig();
			showToast("Imported " + name);
			return true;
		}
		List<ClientData.Profile> list = data.profiles();
		for (int i = 0; i < profileRows.size() && i < list.size(); i++) {
			float[] r = profileRows.get(i);
			ClientData.Profile pr = list.get(i);
			if (Ui.inside(mx, my, r[0], r[1], r[2], r[3])) {
				config.apply(pr.modules);
				data.setActiveProfile(pr.name);
				confirmDelete = null;
				Polar.saveConfig();
				showToast("Loaded " + pr.name);
				return true;
			}
			if (Ui.inside(mx, my, r[4], r[1], r[5], r[3])) {
				data.put(pr.name, config.snapshot());
				data.setActiveProfile(pr.name);
				confirmDelete = null;
				Polar.saveConfig();
				showToast("Updated " + pr.name);
				return true;
			}
			if (Ui.inside(mx, my, r[6], r[1], r[7], r[3])) {
				if (confirmDelete == pr) {
					data.remove(pr);
					confirmDelete = null;
					Polar.saveConfig();
					showToast("Deleted " + pr.name);
				} else {
					confirmDelete = pr;
				}
				return true;
			}
		}
		confirmDelete = null;
		return false;
	}

	@Override
	public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
		SettingsView v = activeSettings();
		return v != null && v.mouseDragged(mx(x));
	}

	@Override
	public boolean mouseReleased(double x, double y, int button) {
		SettingsView v = activeSettings();
		if (v != null) v.mouseReleased();
		return false;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double amount) {
		float mx = mx(x), my = my(y);
		SettingsView v = activeSettings();
		if (v != null) return v.mouseScrolled(mx, my, amount);
		if (page == Page.MODS) {
			if (my >= CHIP_Y - 2 && my < CHIP_Y + CHIP_H + 2 && mx < chipsRight()) {
				chipScrollTarget -= (float) amount * 30;
				return true;
			}
			scrollTarget = (float) Math.max(0, Math.min(maxScroll(), scrollTarget - amount * 24));
		} else if (page == Page.PROFILES) {
			scrollTarget = (float) Math.max(0, Math.min(profilesMaxScroll(), scrollTarget - amount * 24));
		}
		return true;
	}

	private SettingsView activeSettings() {
		if (moduleSettings != null) return moduleSettings;
		return page == Page.SETTINGS && moduleSettings == null ? clientSettings : null;
	}

	@Override
	public boolean keyPressed(KeyInput key) {
		SettingsView v = activeSettings();
		boolean typing = search.focused() || profileName.focused() || (v != null && v.typing());
		if (search.keyPressed(key)) return true;
		if (profileName.keyPressed(key)) return true;
		if (v != null && v.keyPressed(key)) return true;
		if (key.is(KeyInput.Key.ESCAPE) && moduleSettings != null) {
			closeModule();
			return true;
		}
		if (!typing && key.menuKey) {
			onClose();
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(char ch) {
		if (search.charTyped(ch)) return true;
		if (profileName.charTyped(ch)) return true;
		SettingsView v = activeSettings();
		return v != null && v.charTyped(ch);
	}
}
