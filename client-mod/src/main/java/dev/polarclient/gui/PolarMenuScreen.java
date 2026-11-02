package dev.polarclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.polarclient.PolarClient;
import dev.polarclient.PolarKeys;
import dev.polarclient.gui.ui.Fonts;
import dev.polarclient.gui.ui.Icon;
import dev.polarclient.gui.ui.Icons;
import dev.polarclient.gui.ui.Theme;
import dev.polarclient.gui.ui.Ui;
import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.util.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Right Shift menu, in a Material-You style: a big rounded window with a navigation rail
 * (Edit HUD, Home, Mods, Settings), icon cards for mods and slide-in settings pages.
 */
public class PolarMenuScreen extends Screen {
	private enum Page { HOME, MODS, SETTINGS }

	private static final float RAIL_W = 46, RADIUS = 16;
	private static final float CARD_W = 100, CARD_H = 76, CARD_TOP = 57, CARD_GAP = 10, CARD_R = 13;
	private static final float NAV_START = 62, NAV_STEP = 36;

	private static Page lastPage = Page.MODS;

	private final Screen parent;
	private Page page = lastPage;
	private Category filter;
	private EditBox search;
	private SettingsView moduleSettings; // a module's settings page (slides over Mods)
	private SettingsView clientSettings;

	private float wx, wy, ww, wh;
	private float scroll, scrollTarget;
	private long openedAt, pageChangedAt;
	private int pageDirection = 1;
	private final Map<Module, Float> hoverAnim = new HashMap<>();
	private final Map<Module, Float> onAnim = new HashMap<>();
	private final float[] navHover = new float[4];

	public PolarMenuScreen(Screen parent) {
		super(Component.literal("Polar Client"));
		this.parent = parent;
	}

	/** Opens straight into one module's settings (right-click in the HUD editor). */
	public PolarMenuScreen(Screen parent, Module module) {
		this(parent);
		this.page = Page.MODS;
		this.pendingModule = module;
	}

	private Module pendingModule;

	@Override
	protected void init() {
		openedAt = System.currentTimeMillis();
		ww = Math.max(300, Math.min(600, width * 0.85f));
		wh = Math.max(200, Math.min(340, height * 0.80f));
		ww = Math.min(ww, width - 8);
		wh = Math.min(wh, height - 8);
		wx = (width - ww) / 2f;
		wy = (height - wh) / 2f;

		String previous = search != null ? search.getValue() : "";
		search = new EditBox(font, 0, 0, 96, 10, Component.literal("Search"));
		search.setBordered(false);
		search.setMaxLength(32);
		search.setTextColor(0xFFFFFFFF);
		search.setValue(previous);
		search.setResponder(s -> scrollTarget = 0);
		addRenderableWidget(search);

		if (moduleSettings != null) {
			Module m = moduleSettings.module();
			moduleSettings = null;
			openModule(m);
		}
		if (clientSettings != null) {
			clientSettings = null;
		}
		if (pendingModule != null) {
			openModule(pendingModule);
			pendingModule = null;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		lastPage = page;
		PolarClient.saveConfig();
		minecraft.gui.setScreen(parent);
	}

	// ------------------------------------------------------------------ geometry

	private float pageX() {
		return wx + RAIL_W;
	}

	private float pageW() {
		return ww - RAIL_W;
	}

	private float gridTop() {
		return wy + 64;
	}

	private int columns() {
		return Math.max(1, (int) ((pageW() - 26 + CARD_GAP) / (CARD_W + CARD_GAP)));
	}

	private float gridStartX() {
		int cols = columns();
		float total = cols * CARD_W + (cols - 1) * CARD_GAP;
		return pageX() + (pageW() - total) / 2f;
	}

	private List<Module> visibleModules() {
		String q = search.getValue().trim().toLowerCase(Locale.ROOT);
		List<Module> out = new ArrayList<>();
		for (Module m : PolarClient.modules().all()) {
			if (m == PolarClient.modules().client) continue; // lives on the Settings page
			if (filter != null && m.category() != filter) continue;
			if (!q.isEmpty() && !m.name().toLowerCase(Locale.ROOT).contains(q)) continue;
			out.add(m);
		}
		return out;
	}

	private float maxScroll() {
		int n = visibleModules().size(), cols = columns();
		int rows = (n + cols - 1) / cols;
		float content = rows * (CARD_H + CARD_GAP);
		return Math.max(0, content - (wy + wh - 10 - gridTop()));
	}

	private void setPage(Page p) {
		if (p == page && moduleSettings == null) return;
		pageDirection = p.ordinal() >= page.ordinal() ? 1 : -1;
		closeModule();
		page = p;
		scroll = scrollTarget = 0;
		pageChangedAt = System.currentTimeMillis();
		if (p == Page.SETTINGS && clientSettings == null) {
			clientSettings = new SettingsView(PolarClient.modules().client, this::addRenderableWidget, this::removeWidget);
		}
	}

	private void openModule(Module m) {
		if (m.settings().isEmpty()) return;
		closeModule();
		moduleSettings = new SettingsView(m, this::addRenderableWidget, this::removeWidget);
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

	// ------------------------------------------------------------------ rendering

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if (minecraft.level == null) MenuBackground.INSTANCE.render(g, width, height, mouseX, mouseY);
		if (PolarClient.modules().client.menuBlur.on()) extractBlurredBackground(g);
		g.fill(0, 0, width, height, 0x80000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Ui.frame();
		Theme t = Theme.current();
		float open = Ui.easeOut((System.currentTimeMillis() - openedAt) / 220f);
		g.pose().pushMatrix();
		float s = 0.94f + 0.06f * open;
		g.pose().translate(width / 2f, height / 2f);
		g.pose().scale(s);
		g.pose().translate(-width / 2f, -height / 2f);

		// window
		Ui.outlined(g, wx, wy, ww, wh, RADIUS, t.surfaceContainer(), t.hairline(), 1);
		drawRail(g, mouseX, mouseY, t);

		float slide = (1f - Ui.easeOut((System.currentTimeMillis() - pageChangedAt) / 260f)) * 18f * pageDirection;
		g.enableScissor(Math.round(pageX()), Math.round(wy), Math.round(wx + ww), Math.round(wy + wh));
		g.pose().pushMatrix();
		g.pose().translate(slide, 0);
		search.visible = false;
		if (moduleSettings != null) drawModuleSettings(g, mouseX, mouseY, t);
		else if (page == Page.HOME) drawHome(g, mouseX, mouseY, t);
		else if (page == Page.MODS) drawMods(g, mouseX, mouseY, t);
		else drawClientSettings(g, mouseX, mouseY, t);
		g.pose().popMatrix();
		g.disableScissor();
		g.pose().popMatrix();

		super.extractRenderState(g, mouseX, mouseY, a);
	}

	private void drawRail(GuiGraphicsExtractor g, int mouseX, int mouseY, Theme t) {
		Ui.rect(g, wx + 0.5f, wy + 0.5f, RAIL_W, wh - 1, RADIUS, 0, 0, RADIUS, t.surface());
		Ui.vline(g, wx + RAIL_W, wy + 10, wh - 20, 1, t.hairline());
		float cx = wx + RAIL_W / 2f;

		// Edit HUD button
		float ey = wy + 24;
		boolean editHover = Ui.inside(mouseX, mouseY, cx - 11, ey - 11, 22, 22);
		Ui.circle(g, cx, ey, 12, Theme.alpha(t.primary(), 0.6f));
		Ui.circle(g, cx, ey, 11, editHover ? t.surfaceContainerHigh() : t.surface());
		Ui.icon(g, Icon.EDIT, cx, ey, t.onSurface(), Fonts.ICON_M);

		Page[] pages = Page.values();
		String[] icons = {Icon.HOME, Icon.MODS, Icon.SETTINGS};
		String[] labels = {"Home", "Mods", "Settings"};
		for (int i = 0; i < pages.length; i++) {
			float iy = wy + NAV_START + i * NAV_STEP;
			boolean active = page == pages[i];
			boolean hover = Ui.inside(mouseX, mouseY, cx - 14, iy, 28, 26);
			navHover[i] = Ui.approach(navHover[i], hover && !active ? 1f : 0f, 18f);
			if (navHover[i] > 0.01f) Ui.rect(g, cx - 14, iy, 28, 16, 8, Theme.alpha(t.onSurface(), 0.08f * navHover[i]));
			Ui.icon(g, icons[i], cx, iy + 8, active ? t.primary() : t.onSurface(), active ? Fonts.ICON_M_FILL : Fonts.ICON_M);
			Ui.textCentered(g, labels[i], cx, iy + 21, active ? t.onSurface() : t.onSurfaceVariant(), Fonts.SMALL);
			if (active) Ui.rect(g, cx - 8, iy + 27, 16, 1.5f, 0.75f, t.primary());
		}
	}

	private void drawHeader(GuiGraphicsExtractor g, String title, Theme t) {
		Ui.text(g, title, pageX() + 16, wy + 18, t.onSurface(), Fonts.LARGE);
	}

	private void drawSearch(GuiGraphicsExtractor g, int mouseX, int mouseY, Theme t) {
		float sw = 132, sh = 21, sx = pageX() + pageW() - sw - 16, sy = wy + 14;
		boolean focused = search.isFocused();
		if (focused) Ui.outlined(g, sx, sy, sw, sh, sh / 2f, t.surface(), t.primary(), 2);
		else Ui.rect(g, sx, sy, sw, sh, sh / 2f, t.surface());
		Ui.icon(g, Icon.SEARCH, sx + 12, sy + sh / 2f, focused ? t.primary() : t.onSurface(), Fonts.ICON_M);
		if (search.getValue().isEmpty() && !focused) {
			Ui.textMid(g, "Search", sx + 22, sy + sh / 2f, t.onSurfaceVariant(), Fonts.BODY);
		}
		search.visible = true;
		search.setX(Math.round(sx + 22));
		search.setY(Math.round(sy + 6.5f));
		search.setWidth(Math.round(sw - 30));
	}

	private void drawHome(GuiGraphicsExtractor g, int mouseX, int mouseY, Theme t) {
		float cx = pageX() + pageW() / 2f, cy = wy + wh / 2f;
		Draw.texture(g, Draw.MARK_LARGE, Math.round(cx - 22), Math.round(cy - 58), 44, 44);
		Ui.textCentered(g, "Polar Client", cx, cy + 2, t.onSurface(), Fonts.TITLE);
		Ui.textCentered(g, "PvP & Performance Client", cx, cy + 18, t.onSurfaceVariant(), Fonts.SMALL);
		long on = PolarClient.modules().all().stream().filter(m -> m.canToggle() && m.isEnabled()).count();
		String chip = on + " mods on  ·  v" + PolarClient.version();
		float chipW = Fonts.width(chip, Fonts.SMALL) + 16;
		Ui.rect(g, cx - chipW / 2f, cy + 28, chipW, 13, 6.5f, t.primaryContainer());
		Ui.textCentered(g, chip, cx, cy + 34.5f, t.onPrimaryContainer(), Fonts.SMALL);
		Ui.textCentered(g, "Right-click mods to open their settings", cx, wy + wh - 30, t.onSurfaceVariant(), Fonts.SMALL);
		Ui.textCentered(g, "Use the edit button to move your HUD", cx, wy + wh - 20, t.onSurfaceVariant(), Fonts.SMALL);
	}

	private void drawMods(GuiGraphicsExtractor g, int mouseX, int mouseY, Theme t) {
		drawHeader(g, "Mods", t);
		drawSearch(g, mouseX, mouseY, t);
		drawChips(g, mouseX, mouseY, t);

		scroll = Ui.approach(scroll, scrollTarget, 18f);
		float top = gridTop(), bottom = wy + wh - 10;
		boolean mouseInGrid = Ui.inside(mouseX, mouseY, pageX(), top, pageW(), bottom - top);
		g.enableScissor(Math.round(pageX()), Math.round(top - 2), Math.round(wx + ww), Math.round(bottom));
		List<Module> mods = visibleModules();
		int cols = columns();
		float sx = gridStartX();
		if (mods.isEmpty()) Ui.textCentered(g, "No mods match your search", pageX() + pageW() / 2f, top + 30, t.onSurfaceVariant(), Fonts.BODY);
		for (int i = 0; i < mods.size(); i++) {
			Module m = mods.get(i);
			float x = sx + (i % cols) * (CARD_W + CARD_GAP);
			float y = top + (i / cols) * (CARD_H + CARD_GAP) - scroll;
			if (y + CARD_H < top - 2 || y > bottom) continue;
			boolean hover = mouseInGrid && Ui.inside(mouseX, mouseY, x, y, CARD_W, CARD_H);
			drawCard(g, m, x, y, hover, t);
		}
		g.disableScissor();
		float max = maxScroll();
		if (max > 0) {
			float h = bottom - top, thumb = Math.max(16, h * h / (h + max));
			Ui.rect(g, wx + ww - 6, top + (h - thumb) * (scroll / max), 2, thumb, 1, Theme.alpha(t.onSurface(), 0.25f));
		}
	}

	private void drawChips(GuiGraphicsExtractor g, int mouseX, int mouseY, Theme t) {
		float x = pageX() + 16, y = wy + 42;
		for (int i = -1; i < Category.values().length; i++) {
			Category c = i < 0 ? null : Category.values()[i];
			String label = c == null ? "All" : c.label;
			float w = Fonts.width(label, Fonts.SMALL) + (filter == c ? 22 : 14);
			boolean selected = filter == c;
			boolean hover = Ui.inside(mouseX, mouseY, x, y, w, 14);
			if (selected) {
				Ui.rect(g, x, y, w, 14, 5, t.primaryContainer());
				Ui.icon(g, Icon.CHECK, x + 8, y + 7, t.onPrimaryContainer(), Fonts.ICON_S);
				Ui.textMid(g, label, x + 14, y + 7, t.onPrimaryContainer(), Fonts.SMALL);
			} else {
				Ui.outlined(g, x, y, w, 14, 5, hover ? t.surfaceContainerHigh() : t.surfaceContainer(), t.outlineVariant(), 1);
				Ui.textMid(g, label, x + 7, y + 7, t.onSurfaceVariant(), Fonts.SMALL);
			}
			x += w + 5;
		}
	}

	private void drawCard(GuiGraphicsExtractor g, Module m, float x, float y, boolean hover, Theme t) {
		float hv = Ui.approach(hoverAnim.getOrDefault(m, 0f), hover ? 1f : 0f, 16f);
		hoverAnim.put(m, hv);
		boolean on = m.canToggle() && m.isEnabled();
		float onF = Ui.approach(onAnim.getOrDefault(m, on ? 1f : 0f), on ? 1f : 0f, 12f);
		onAnim.put(m, onF);

		int border = hv > 0.02f ? Theme.mix(0xFF4A4A4A, t.primary(), hv) : t.hairline();
		Ui.rect(g, x, y, CARD_W, CARD_H, CARD_R, border);
		float i = 1f / Ui.scale() * (hv > 0.5f ? 2 : 1);
		float stripH = CARD_H - CARD_TOP;
		Ui.rect(g, x + i, y + i, CARD_W - 2 * i, CARD_TOP - i, CARD_R - i, CARD_R - i, 0, 0, t.surface());
		Ui.rect(g, x + i, y + CARD_TOP, CARD_W - 2 * i, stripH - i, 0, 0, CARD_R - i, CARD_R - i,
			hv > 0.02f ? Theme.mix(t.surfaceContainerLow(), t.surfaceContainerHigh(), hv * 0.5f) : t.surfaceContainerLow());
		if (onF > 0.01f) {
			// fill wipes in from the left when a mod is switched on
			g.enableScissor(Math.round(x), Math.round(y + CARD_TOP), Math.round(x + CARD_W * onF), Math.round(y + CARD_H));
			Ui.rect(g, x + i, y + CARD_TOP, CARD_W - 2 * i, stripH - i, 0, 0, CARD_R - i, CARD_R - i, t.primaryContainer());
			g.disableScissor();
		}
		Ui.icon(g, Icons.of(m), x + CARD_W / 2f, y + CARD_TOP / 2f + 1, on ? t.onSurface() : Theme.alpha(t.onSurface(), 0.85f), Fonts.ICON_L);
		String name = Fonts.ellipsize(m.name(), Fonts.BODY, (int) CARD_W - 12);
		Ui.textCentered(g, name, x + CARD_W / 2f, y + CARD_TOP + stripH / 2f, on ? t.onPrimaryContainer() : t.onSurface(), Fonts.BODY);
		if (hv > 0.02f && !m.settings().isEmpty()) {
			Ui.icon(g, Icon.TUNE, x + CARD_W - 10, y + 10, Theme.alpha(t.onSurfaceVariant(), hv), Fonts.ICON_S);
		}
	}

	private void drawModuleSettings(GuiGraphicsExtractor g, int mouseX, int mouseY, Theme t) {
		Module m = moduleSettings.module();
		float bx = pageX() + 16, by = wy + 14;
		drawIconButton(g, Icon.BACK, bx + 10.5f, by + 10.5f, mouseX, mouseY, t);
		Ui.text(g, m.name(), bx + 28, wy + 18, t.onSurface(), Fonts.LARGE);
		float right = pageX() + pageW() - 16;
		drawIconButton(g, Icon.RESET, right - 10.5f, by + 10.5f, mouseX, mouseY, t);
		if (m.canToggle()) drawHeaderSwitch(g, m.isEnabled(), right - 30 - 26, by + 2.5f, t);
		moduleSettings.render(g, pageX() + 16, wy + 46, pageW() - 32, wh - 54, mouseX, mouseY, t);
	}

	private void drawClientSettings(GuiGraphicsExtractor g, int mouseX, int mouseY, Theme t) {
		drawHeader(g, "Settings", t);
		if (clientSettings == null) {
			clientSettings = new SettingsView(PolarClient.modules().client, this::addRenderableWidget, this::removeWidget);
		}
		clientSettings.render(g, pageX() + 16, wy + 46, pageW() - 32, wh - 54, mouseX, mouseY, t);
	}

	private void drawIconButton(GuiGraphicsExtractor g, String icon, float cx, float cy, int mouseX, int mouseY, Theme t) {
		boolean hover = Ui.inside(mouseX, mouseY, cx - 10.5f, cy - 10.5f, 21, 21);
		Ui.circle(g, cx, cy, 10.5f, hover ? t.surfaceContainerHighest() : t.surfaceContainerHigh());
		Ui.icon(g, icon, cx, cy, t.onSurface(), Fonts.ICON_M);
	}

	private void drawHeaderSwitch(GuiGraphicsExtractor g, boolean on, float x, float y, Theme t) {
		if (on) Ui.rect(g, x, y, 26, 16, 8, t.primary());
		else Ui.outlined(g, x, y, 26, 16, 8, t.surfaceContainerHighest(), t.outlineVariant(), 2);
		Ui.circle(g, on ? x + 18 : x + 8, y + 8, on ? 6 : 4, on ? t.onPrimary() : t.onSurfaceVariant());
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		int button = event.button();
		if (super.mouseClicked(event, doubleClick)) return true;
		if (!Ui.inside(mx, my, wx, wy, ww, wh)) {
			onClose();
			return true;
		}
		float cx = wx + RAIL_W / 2f;
		if (Ui.inside(mx, my, cx - 11, wy + 13, 22, 22)) {
			lastPage = page;
			minecraft.gui.setScreen(new HudEditorScreen(this));
			return true;
		}
		for (int i = 0; i < Page.values().length; i++) {
			float iy = wy + NAV_START + i * NAV_STEP;
			if (Ui.inside(mx, my, cx - 16, iy - 2, 32, 30)) {
				setPage(Page.values()[i]);
				return true;
			}
		}
		if (moduleSettings != null) return clickModuleSettings(mx, my, button);
		if (page == Page.MODS) return clickMods(mx, my, button);
		if (page == Page.SETTINGS && clientSettings != null) return clientSettings.mouseClicked(mx, my, button);
		return false;
	}

	private boolean clickModuleSettings(double mx, double my, int button) {
		float bx = pageX() + 16, by = wy + 14, right = pageX() + pageW() - 16;
		if (Ui.inside(mx, my, bx, by, 21, 21)) {
			closeModule();
			return true;
		}
		if (Ui.inside(mx, my, right - 21, by, 21, 21)) {
			moduleSettings.module().resetSettings();
			moduleSettings.refreshTextBoxes();
			return true;
		}
		Module m = moduleSettings.module();
		if (m.canToggle() && Ui.inside(mx, my, right - 30 - 26, by + 2.5f, 26, 16)) {
			m.toggle();
			return true;
		}
		return moduleSettings.mouseClicked(mx, my, button);
	}

	private boolean clickMods(double mx, double my, int button) {
		float x = pageX() + 16, y = wy + 42;
		for (int i = -1; i < Category.values().length; i++) {
			Category c = i < 0 ? null : Category.values()[i];
			String label = c == null ? "All" : c.label;
			float w = Fonts.width(label, Fonts.SMALL) + (filter == c ? 22 : 14);
			if (Ui.inside(mx, my, x, y, w, 14)) {
				filter = c;
				scroll = scrollTarget = 0;
				return true;
			}
			x += w + 5;
		}
		float top = gridTop(), bottom = wy + wh - 10;
		if (!Ui.inside(mx, my, pageX(), top, pageW(), bottom - top)) return false;
		List<Module> mods = visibleModules();
		int cols = columns();
		float sx = gridStartX();
		for (int i = 0; i < mods.size(); i++) {
			float cx = sx + (i % cols) * (CARD_W + CARD_GAP);
			float cy = top + (i / cols) * (CARD_H + CARD_GAP) - scroll;
			if (!Ui.inside(mx, my, cx, cy, CARD_W, CARD_H)) continue;
			Module m = mods.get(i);
			boolean onTune = !m.settings().isEmpty() && Ui.inside(mx, my, cx + CARD_W - 18, cy + 2, 16, 16);
			if (button == InputConstants.MOUSE_BUTTON_RIGHT || onTune || !m.canToggle()) openModule(m);
			else m.toggle();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		SettingsView v = activeSettings();
		if (v != null && v.mouseDragged(event.x())) return true;
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		SettingsView v = activeSettings();
		if (v != null) v.mouseReleased();
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		SettingsView v = activeSettings();
		if (v != null) return v.mouseScrolled(x, y, scrollY);
		if (page == Page.MODS) scrollTarget = (float) Math.max(0, Math.min(maxScroll(), scrollTarget - scrollY * 26));
		return true;
	}

	private SettingsView activeSettings() {
		if (moduleSettings != null) return moduleSettings;
		return page == Page.SETTINGS ? clientSettings : null;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		boolean typing = getFocused() instanceof EditBox box && box.isFocused();
		if (event.isEscape() && moduleSettings != null) {
			closeModule();
			return true;
		}
		if (!typing && PolarKeys.OPEN_MENU.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}
}
