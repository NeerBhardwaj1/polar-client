package dev.polarclient.gui;

import dev.polarclient.gfx.Gfx;
import dev.polarclient.gui.ui.Fonts;
import dev.polarclient.gui.ui.Icon;
import dev.polarclient.gui.ui.Icons;
import dev.polarclient.gui.ui.TextField;
import dev.polarclient.gui.ui.Theme;
import dev.polarclient.gui.ui.Ui;
import dev.polarclient.module.Module;
import dev.polarclient.setting.ActionSetting;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ColorSetting;
import dev.polarclient.setting.ModeSetting;
import dev.polarclient.setting.NumberSetting;
import dev.polarclient.setting.Setting;
import dev.polarclient.setting.TextSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A scrolling list of rounded "setting bars" (icon, name, current value, control on the right). */
final class SettingsView {
	static final float BAR_H = 30, COLOR_BAR_H = 48, GAP = 5, RADIUS = 6.4f;
	private static final float SWITCH_W = 26, SWITCH_H = 16, SLIDER_W = 100, FIELD_W = 104, FIELD_H = 16;

	private final Module module;
	private final Map<TextSetting, TextField> boxes = new LinkedHashMap<TextSetting, TextField>();
	private final Map<Setting<?>, Float> switchAnim = new HashMap<Setting<?>, Float>();

	private float x, y, w, h;
	private float scroll, scrollTarget;
	private NumberSetting draggingSlider;
	private ColorSetting draggingAlpha;
	private float dragX, dragW;

	SettingsView(Module module) {
		this.module = module;
		for (Setting<?> s : module.settings()) {
			if (s instanceof TextSetting) {
				final TextSetting t = (TextSetting) s;
				TextField box = new TextField(t.maxLength());
				box.setValue(t.get());
				box.setTextColor(0xFFFFFFFF);
				box.setListener(t::set);
				box.visible = false;
				boxes.put(t, box);
			}
		}
	}

	Module module() {
		return module;
	}

	void dispose() {
		boxes.clear();
	}

	void refreshTextBoxes() {
		for (Map.Entry<TextSetting, TextField> e : boxes.entrySet()) e.getValue().setValue(e.getKey().get());
	}

	/** A text box has keyboard focus (typing shouldn't trigger shortcuts). */
	boolean typing() {
		for (TextField box : boxes.values()) if (box.focused()) return true;
		return false;
	}

	boolean keyPressed(KeyInput k) {
		for (TextField box : boxes.values()) if (box.keyPressed(k)) return true;
		return false;
	}

	boolean charTyped(char c) {
		for (TextField box : boxes.values()) if (box.charTyped(c)) return true;
		return false;
	}

	private List<Setting<?>> visible() {
		List<Setting<?>> out = new ArrayList<Setting<?>>();
		for (Setting<?> s : module.settings()) if (s.isVisible()) out.add(s);
		return out;
	}

	private static float barHeight(Setting<?> s) {
		return s instanceof ColorSetting ? COLOR_BAR_H : BAR_H;
	}

	private float contentHeight() {
		float total = 0;
		for (Setting<?> s : visible()) total += barHeight(s) + GAP;
		return total;
	}

	private float maxScroll() {
		return Math.max(0, contentHeight() - h + 4);
	}

	// ------------------------------------------------------------------ rendering

	void render(Gfx g, float x, float y, float w, float h, int mouseX, int mouseY, Theme t) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		scroll = Ui.approach(scroll, scrollTarget, 18f);
		for (TextField box : boxes.values()) box.visible = false;
		g.scissor(Math.round(x) - 2, Math.round(y), Math.round(x + w) + 2, Math.round(y + h));
		float by = y - scroll;
		boolean mouseIn = Ui.inside(mouseX, mouseY, x, y, w, h);
		for (Setting<?> s : visible()) {
			float bh = barHeight(s);
			if (by + bh >= y && by <= y + h) drawBar(g, s, x, by, w, bh, mouseIn ? mouseX : -1, mouseY, t);
			by += bh + GAP;
		}
		g.endScissor();
		for (TextField box : boxes.values()) {
			if (box.visible && (box.y() < y || box.y() + 10 > y + h)) box.visible = false;
		}
		float max = maxScroll();
		if (max > 0) {
			float thumb = Math.max(16, h * h / (h + max));
			float ty = y + (h - thumb) * (scroll / max);
			Ui.rect(g, x + w + 4, ty, 2, thumb, 1, Theme.alpha(t.onSurface(), 0.25f));
		}
	}

	private String summary(Setting<?> s) {
		if (s instanceof BooleanSetting) return ((BooleanSetting) s).on() ? "Enabled" : "Disabled";
		if (s instanceof NumberSetting) return ((NumberSetting) s).display();
		if (s instanceof ModeSetting) return ((ModeSetting) s).get();
		if (s instanceof ColorSetting) {
			ColorSetting c = (ColorSetting) s;
			return c.chroma() ? "Chroma" : String.format(Locale.ROOT, "#%06X", c.get() & 0xFFFFFF)
				+ (c.allowsAlpha() ? "  \u00b7  " + Math.round(c.alpha() / 2.55f) + "% opacity" : "");
		}
		if (s instanceof TextSetting) return "Click the box to type";
		if (s instanceof ActionSetting) return ((ActionSetting) s).get();
		return "";
	}

	private void drawBar(Gfx g, Setting<?> s, float x, float y, float w, float h, int mx, int my, Theme t) {
		boolean hover = Ui.inside(mx, my, x, y, w, h);
		int highlight = Theme.mix(t.primary(), 0xFF40E0FF, 0.45f);
		Ui.rect(g, x, y, w, h, RADIUS, hover ? Theme.mix(0xFF1B3B60, highlight, 0.45f) : 0xFF1B3B60);
		Ui.rect(g, x + 0.65f, y + 0.65f, w - 1.3f, h - 1.3f, RADIUS - 0.65f, hover ? 0xF00A1E3A : 0xF0081931);
		float rowMid = y + BAR_H / 2f;
		Ui.icon(g, Icons.of(s), x + 15, rowMid, 0xFFD2DDEA, Fonts.ICON_M);
		Ui.text(g, s.name(), x + 28, rowMid - 7.5f, 0xFFF1F6FC, Fonts.BODY);
		Ui.text(g, summary(s), x + 28, rowMid + 2.5f, 0xFFA8B7CB, Fonts.SMALL);
		float right = x + w - 11;
		if (s instanceof BooleanSetting) drawSwitch(g, (BooleanSetting) s, right - SWITCH_W, rowMid - SWITCH_H / 2f, t);
		else if (s instanceof NumberSetting) drawSlider(g, ((NumberSetting) s).fraction(), right - SLIDER_W, rowMid - 9.5f, SLIDER_W, t);
		else if (s instanceof ModeSetting) drawCombo(g, ((ModeSetting) s).get(), right, rowMid, mx, my, t);
		else if (s instanceof TextSetting) drawField(g, (TextSetting) s, right - FIELD_W, rowMid - FIELD_H / 2f, t);
		else if (s instanceof ColorSetting) drawColor(g, (ColorSetting) s, x, y, right, mx, my, t);
		else if (s instanceof ActionSetting) drawButton(g, ((ActionSetting) s).button(), right, rowMid, mx, my, t);
	}

	private static float buttonWidth(String label) {
		return Fonts.width(label, Fonts.BODY) + 22;
	}

	private void drawButton(Gfx g, String label, float right, float mid, int mx, int my, Theme t) {
		float bw = buttonWidth(label), x = right - bw, y = mid - 8;
		boolean hover = Ui.inside(mx, my, x, y, bw, 16);
		int chip = Theme.mix(t.primary(), 0xFF4FB0FF, 0.45f);
		Ui.gradientRect(g, x, y, bw, 16, 8, Theme.mix(chip, 0xFFFFFFFF, hover ? 0.22f : 0.12f), Theme.mix(chip, 0xFF2A7BD8, 0.25f));
		Ui.textCentered(g, label, x + bw / 2f, mid, 0xFFFFFFFF, Fonts.BODY);
	}

	private void drawSwitch(Gfx g, BooleanSetting b, float x, float y, Theme t) {
		Float prev = switchAnim.get(b);
		float f = Ui.approach(prev == null ? (b.on() ? 1f : 0f) : prev, b.on() ? 1f : 0f, 16f);
		switchAnim.put(b, f);
		int on = Theme.mix(t.primary(), 0xFF0B5FD0, 0.78f);
		Ui.rect(g, x, y, SWITCH_W, SWITCH_H, SWITCH_H / 2f, Theme.lerp(0xFF3A4760, Theme.mix(on, 0xFFFFFFFF, 0.15f), f));
		Ui.rect(g, x + 0.7f, y + 0.7f, SWITCH_W - 1.4f, SWITCH_H - 1.4f, SWITCH_H / 2f - 0.7f, Theme.lerp(0xFF222C3D, on, f));
		float r = SWITCH_H / 2f - 2.2f;
		Ui.circle(g, x + SWITCH_H / 2f + (SWITCH_W - SWITCH_H) * f, y + SWITCH_H / 2f, r, Theme.lerp(0xFF7C8AA3, 0xFFF0F5FB, f));
	}

	/** Material 3 "expressive" slider: tall thin handle splitting an active and an inactive track. */
	static void drawSlider(Gfx g, double fraction, float x, float y, float w, Theme t) {
		float hx = x + 3 + (float) fraction * (w - 6);
		float trackY = y + 5.5f, trackH = 8;
		if (hx - 3 - x > 1) Ui.rect(g, x, trackY, hx - 3 - x, trackH, 4, 2, 2, 4, t.primary());
		if (x + w - hx - 3 > 1) Ui.rect(g, hx + 3, trackY, x + w - hx - 3, trackH, 2, 4, 4, 2, t.primaryContainer());
		Ui.circle(g, x + w - 4, trackY + trackH / 2f, 1, t.primary());
		Ui.rect(g, hx - 1, y, 2, 19, 1, t.primary());
	}

	private float comboWidth(String value) {
		return Math.max(64, Fonts.width(value, Fonts.BODY) + 34);
	}

	private void drawCombo(Gfx g, String value, float right, float mid, int mx, int my, Theme t) {
		float cw = comboWidth(value), x = right - cw, y = mid - 8;
		boolean hover = Ui.inside(mx, my, x, y, cw, 16);
		int chip = Theme.mix(t.primary(), 0xFF4FB0FF, 0.45f);
		Ui.gradientRect(g, x, y, cw, 16, 8, Theme.mix(chip, 0xFFFFFFFF, hover ? 0.22f : 0.12f), Theme.mix(chip, 0xFF2A7BD8, 0.25f));
		Ui.icon(g, Icon.CHEVRON_LEFT, x + 8, mid, 0xFFFFFFFF, Fonts.ICON_S);
		Ui.icon(g, Icon.CHEVRON_RIGHT, x + cw - 8, mid, 0xFFFFFFFF, Fonts.ICON_S);
		Ui.textCentered(g, value, x + cw / 2f, mid, 0xFFFFFFFF, Fonts.BODY);
	}

	private void drawField(Gfx g, TextSetting s, float x, float y, Theme t) {
		TextField box = boxes.get(s);
		boolean focused = box != null && box.focused();
		Ui.outlined(g, x, y, FIELD_W, FIELD_H, FIELD_H / 2f, 0xFF061226, focused ? t.primary() : 0xFF2A4A72, focused ? 2 : 1);
		if (box != null) {
			box.setBounds(Math.round(x + 8), Math.round(y + 4), (int) FIELD_W - 16);
			box.visible = true;
			box.render(g);
		}
	}

	private void drawColor(Gfx g, ColorSetting c, float x, float y, float right, int mx, int my, Theme t) {
		float mid = y + BAR_H / 2f;
		Ui.outlined(g, right - 14, mid - 7, 14, 14, 7, c.argb() | 0xFF000000, t.hairline(), 1);
		float sy = y + 36;
		float sx = x + 28;
		for (int rgb : ColorSetting.PALETTE) {
			boolean picked = !c.chroma() && (c.get() & 0xFFFFFF) == (rgb & 0xFFFFFF);
			if (picked) Ui.circle(g, sx + 5, sy, 6.5f, t.onSurface());
			Ui.circle(g, sx + 5, sy, 5, rgb | 0xFF000000);
			sx += 14;
		}
		float chx = sx + 4;
		if (c.chroma()) Ui.rect(g, chx, sy - 6, 44, 12, 6, t.primary());
		else Ui.outlined(g, chx, sy - 6, 44, 12, 6, t.surface(), t.outlineVariant(), 1);
		Ui.textCentered(g, "Chroma", chx + 22, sy, c.chroma() ? t.onPrimary() : t.onSurfaceVariant(), Fonts.SMALL);
		if (c.allowsAlpha()) {
			float ax = chx + 52, aw = Math.max(40, right - ax);
			drawSlider(g, c.alpha() / 255.0, ax, sy - 9.5f, aw, t);
		}
	}

	// ------------------------------------------------------------------ input

	boolean mouseClicked(double mx, double my, int button) {
		boolean onBox = false;
		for (TextField box : boxes.values()) onBox |= box.mouseClicked(mx, my);
		if (onBox) return true;
		if (!Ui.inside(mx, my, x, y, w, h)) return false;
		float by = y - scroll;
		for (Setting<?> s : visible()) {
			float bh = barHeight(s);
			if (Ui.inside(mx, my, x, by, w, bh)) {
				clickBar(s, mx, my, button, by);
				return true;
			}
			by += bh + GAP;
		}
		return false;
	}

	private void clickBar(Setting<?> s, double mx, double my, int button, float by) {
		float right = x + w - 11;
		float mid = by + BAR_H / 2f;
		if (s instanceof BooleanSetting) {
			((BooleanSetting) s).toggle();
		} else if (s instanceof NumberSetting) {
			NumberSetting n = (NumberSetting) s;
			float sx = right - SLIDER_W;
			if (Ui.inside(mx, my, sx - 6, mid - 12, SLIDER_W + 12, 24)) {
				draggingSlider = n;
				dragX = sx;
				dragW = SLIDER_W;
				n.setFraction((mx - sx - 3) / (SLIDER_W - 6));
			}
		} else if (s instanceof ModeSetting) {
			ModeSetting m = (ModeSetting) s;
			float cw = comboWidth(m.get()), cx = right - cw;
			if (Ui.inside(mx, my, cx, mid - 8, cw / 2f, 16) || button == View.RIGHT) m.cycle(-1);
			else m.cycle(1);
		} else if (s instanceof ColorSetting) {
			clickColor((ColorSetting) s, mx, my, by, right);
		} else if (s instanceof ActionSetting) {
			ActionSetting a = (ActionSetting) s;
			if (Ui.inside(mx, my, right - buttonWidth(a.button()), mid - 9, buttonWidth(a.button()), 18)) {
				dev.polarclient.platform.Platform.get().playClickSound();
				a.run();
			}
		}
	}

	private void clickColor(ColorSetting c, double mx, double my, float by, float right) {
		float sy = by + 36;
		float sx = x + 28;
		for (int rgb : ColorSetting.PALETTE) {
			if (Ui.inside(mx, my, sx - 1, sy - 6, 12, 12)) {
				c.pick(rgb | 0xFF000000);
				return;
			}
			sx += 14;
		}
		float chx = sx + 4;
		if (Ui.inside(mx, my, chx, sy - 6, 44, 12)) {
			c.setChroma(!c.chroma());
			return;
		}
		if (c.allowsAlpha()) {
			float ax = chx + 52, aw = Math.max(40, right - ax);
			if (Ui.inside(mx, my, ax - 4, sy - 10, aw + 8, 20)) {
				draggingAlpha = c;
				dragX = ax;
				dragW = aw;
				c.setAlpha((int) Math.round((mx - ax - 3) / (aw - 6) * 255));
			}
		}
	}

	boolean mouseDragged(double mx) {
		if (draggingSlider != null) {
			draggingSlider.setFraction((mx - dragX - 3) / (dragW - 6));
			return true;
		}
		if (draggingAlpha != null) {
			draggingAlpha.setAlpha((int) Math.round((mx - dragX - 3) / (dragW - 6) * 255));
			return true;
		}
		return false;
	}

	void mouseReleased() {
		draggingSlider = null;
		draggingAlpha = null;
	}

	boolean mouseScrolled(double mx, double my, double amount) {
		if (!Ui.inside(mx, my, x, y, w, h)) return false;
		scrollTarget = (float) Math.max(0, Math.min(maxScroll(), scrollTarget - amount * 24));
		return true;
	}
}
