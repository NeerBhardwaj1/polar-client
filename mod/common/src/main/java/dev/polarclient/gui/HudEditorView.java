package dev.polarclient.gui;

import dev.polarclient.Polar;
import dev.polarclient.gfx.Gfx;
import dev.polarclient.gui.ui.Fonts;
import dev.polarclient.gui.ui.Icon;
import dev.polarclient.gui.ui.Theme;
import dev.polarclient.gui.ui.Ui;
import dev.polarclient.module.HudModule;

import java.util.ArrayList;
import java.util.List;

/** Drag HUD mods around, scroll to resize, right-click for settings. Snaps to edges, centre and other mods. */
public class HudEditorView extends View {
	private static final int SNAP = 4;
	private static final float BAR_W = 196, BAR_H = 30;

	private HudModule dragging;
	private double offX, offY;
	private Integer guideX, guideY;

	public HudEditorView(Object parent) {
		super(parent);
	}

	@Override
	public String title() {
		return "Polar HUD Editor";
	}

	@Override
	public boolean pausesGame() {
		return false;
	}

	@Override
	public void onClose() {
		Polar.saveConfig();
		p().show(parent);
	}

	private List<HudModule> enabled() {
		List<HudModule> out = new ArrayList<HudModule>();
		for (HudModule m : Polar.modules().hud()) if (m.isEnabled()) out.add(m);
		return out;
	}

	@Override
	public void renderBackground(Gfx g, int mouseX, int mouseY) {
		if (!p().inWorld()) {
			MenuBackground.INSTANCE.render(g, width, height, mouseX, mouseY);
			g.fill(0, 0, width, height, 0x66000000);
		}
		// faint grid + centre lines
		Theme t = Theme.current();
		for (int x = 0; x < width; x += 20) Ui.vline(g, x, 0, height, 1, 0x14FFFFFF);
		for (int y = 0; y < height; y += 20) Ui.hline(g, 0, y, width, 1, 0x14FFFFFF);
		Ui.vline(g, width / 2f, 0, height, 1, Theme.alpha(t.primary(), 0.35f));
		Ui.hline(g, 0, height / 2f, width, 1, Theme.alpha(t.primary(), 0.35f));
	}

	@Override
	public void render(Gfx g, int mouseX, int mouseY, float a) {
		HudModule hovered = null;
		for (HudModule m : enabled()) {
			m.render(g, width, height, true);
			if (dragging == null && m.contains(mouseX, mouseY)) hovered = m;
		}
		Ui.frame();
		Theme t = Theme.current();
		for (HudModule m : enabled()) {
			boolean active = m == dragging || m == hovered;
			if (active) g.fill(m.lastX() - 2, m.lastY() - 2, m.lastX() + m.lastWidth() + 2, m.lastY() + m.lastHeight() + 2, Theme.alpha(t.primary(), 0.12f));
			Ui.stroke(g, m.lastX() - 2, m.lastY() - 2, m.lastWidth() + 4, m.lastHeight() + 4, active ? t.primary() : Theme.alpha(t.primary(), 0.45f));
		}
		HudModule labelled = dragging != null ? dragging : hovered;
		if (labelled != null) {
			String label = labelled.name() + "  \u00b7  " + Math.round(labelled.scale.get() * 100) + "%";
			float lw = Fonts.width(label, Fonts.SMALL) + 12;
			float lx = Math.max(2, Math.min(width - lw - 2, labelled.lastX() - 2));
			float ly = labelled.lastY() > 18 ? labelled.lastY() - 17 : labelled.lastY() + labelled.lastHeight() + 5;
			Ui.rect(g, lx, ly, lw, 12, 6, t.surfaceContainerHigh());
			Ui.textMid(g, label, lx + 6, ly + 6, t.onSurface(), Fonts.SMALL);
		}
		if (guideX != null) Ui.vline(g, guideX, 0, height, 1, t.primary());
		if (guideY != null) Ui.hline(g, 0, guideY, width, 1, t.primary());

		// floating toolbar
		float bx = width / 2f - BAR_W / 2f, by = height - BAR_H - 12;
		Ui.outlined(g, bx, by, BAR_W, BAR_H, BAR_H / 2f, t.surfaceContainer(), t.hairline(), 1);
		toolButton(g, Icon.MODS, "Mods", bx + 6, by + 5, 58, mouseX, mouseY, t, false);
		toolButton(g, Icon.RESET, "Reset", bx + 68, by + 5, 58, mouseX, mouseY, t, false);
		toolButton(g, Icon.CHECK, "Done", bx + 130, by + 5, 60, mouseX, mouseY, t, true);
		if (enabled().isEmpty()) {
			Ui.textCentered(g, "No HUD mods are on. Open Mods to enable some.", width / 2f, height / 2f - 20, t.onSurface(), Fonts.BODY);
		} else {
			Ui.textCentered(g, "Drag to move  \u00b7  Scroll to resize  \u00b7  Right-click for settings", width / 2f, by - 10, Theme.alpha(t.onSurface(), 0.8f), Fonts.SMALL);
		}
	}

	private void toolButton(Gfx g, String icon, String label, float x, float y, float w,
							int mouseX, int mouseY, Theme t, boolean primary) {
		boolean hover = Ui.inside(mouseX, mouseY, x, y, w, 20);
		int bg = primary ? (hover ? Theme.mix(t.primary(), 0xFFFFFFFF, 0.12f) : t.primary())
			: (hover ? t.surfaceContainerHighest() : t.surfaceContainerHigh());
		int fg = primary ? t.onPrimary() : t.onSurface();
		Ui.rect(g, x, y, w, 20, 10, bg);
		float tw = 12 + 4 + Fonts.width(label, Fonts.BODY);
		float sx = x + (w - tw) / 2f;
		Ui.icon(g, icon, sx + 6, y + 10, fg, Fonts.ICON_M);
		Ui.textMid(g, label, sx + 16, y + 10, fg, Fonts.BODY);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		float bx = width / 2f - BAR_W / 2f, by = height - BAR_H - 12;
		if (Ui.inside(mx, my, bx + 6, by + 5, 58, 20)) {
			p().open(new PolarMenuView(p().currentScreen()));
			return true;
		}
		if (Ui.inside(mx, my, bx + 68, by + 5, 58, 20)) {
			for (HudModule m : Polar.modules().hud()) {
				m.resetPosition();
				m.scale.reset();
			}
			return true;
		}
		if (Ui.inside(mx, my, bx + 130, by + 5, 60, 20)) {
			onClose();
			return true;
		}
		if (Ui.inside(mx, my, bx, by, BAR_W, BAR_H)) return true;
		List<HudModule> mods = enabled();
		for (int i = mods.size() - 1; i >= 0; i--) {
			HudModule m = mods.get(i);
			if (!m.contains(mx, my)) continue;
			if (button == RIGHT) {
				p().open(new PolarMenuView(p().currentScreen(), m));
			} else {
				dragging = m;
				offX = mx - m.lastX();
				offY = my - m.lastY();
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (dragging == null) return false;
		int w = dragging.lastWidth(), h = dragging.lastHeight();
		int x = (int) Math.round(mx - offX);
		int y = (int) Math.round(my - offY);
		guideX = null;
		guideY = null;
		// snap targets: screen edges/centre, then other mods' edges
		int[] snappedX = snap(x, w, new int[]{0, width / 2, width}, true);
		int[] snappedY = snap(y, h, new int[]{0, height / 2, height}, false);
		x = snappedX[0];
		y = snappedY[0];
		if (snappedX[1] >= 0) guideX = snappedX[1];
		if (snappedY[1] >= 0) guideY = snappedY[1];
		dragging.setPosition(x, y, width, height);
		return true;
	}

	/** Returns {newPos, guideLine or -1}. Tries start, centre and end of the box against each line. */
	private int[] snap(int pos, int size, int[] screenLines, boolean horizontal) {
		List<Integer> lines = new ArrayList<Integer>();
		for (int l : screenLines) lines.add(l);
		for (HudModule m : enabled()) {
			if (m == dragging) continue;
			int start = horizontal ? m.lastX() : m.lastY();
			int len = horizontal ? m.lastWidth() : m.lastHeight();
			lines.add(start);
			lines.add(start + len);
		}
		for (int line : lines) {
			if (Math.abs(pos - line) <= SNAP) return new int[]{line, line};
			if (Math.abs(pos + size - line) <= SNAP) return new int[]{line - size, line};
			if (Math.abs(pos + size / 2 - line) <= SNAP) return new int[]{line - size / 2, line};
		}
		return new int[]{pos, -1};
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		dragging = null;
		guideX = null;
		guideY = null;
		return false;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double amount) {
		for (HudModule m : enabled()) {
			if (m.contains(x, y)) {
				m.scale.set(m.scale.get() + (amount > 0 ? 0.05 : -0.05));
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyInput key) {
		if (key.menuKey) {
			p().open(new PolarMenuView(p().currentScreen()));
			return true;
		}
		return false;
	}
}
