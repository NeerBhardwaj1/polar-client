package dev.polarclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.polarclient.gui.ui.Fonts;
import dev.polarclient.gui.ui.Icon;
import dev.polarclient.gui.ui.Icons;
import dev.polarclient.gui.ui.Theme;
import dev.polarclient.gui.ui.Ui;
import dev.polarclient.module.Module;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ColorSetting;
import dev.polarclient.setting.ModeSetting;
import dev.polarclient.setting.NumberSetting;
import dev.polarclient.setting.Setting;
import dev.polarclient.setting.TextSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** A scrolling list of rounded "setting bars" (icon, name, current value, control on the right). */
final class SettingsView {
	static final float BAR_H = 34, COLOR_BAR_H = 50, GAP = 8, RADIUS = 9;
	private static final float SWITCH_W = 26, SWITCH_H = 16, SLIDER_W = 100, FIELD_W = 104, FIELD_H = 16;

	private final Module module;
	private final Consumer<EditBox> addWidget, removeWidget;
	private final Map<TextSetting, EditBox> boxes = new HashMap<>();
	private final Map<Setting<?>, Float> switchAnim = new HashMap<>();

	private float x, y, w, h;
	private float scroll, scrollTarget;
	private NumberSetting draggingSlider;
	private ColorSetting draggingAlpha;
	private float dragX, dragW;

	SettingsView(Module module, Consumer<EditBox> addWidget, Consumer<EditBox> removeWidget) {
		this.module = module;
		this.addWidget = addWidget;
		this.removeWidget = removeWidget;
		var font = Minecraft.getInstance().font;
		for (Setting<?> s : module.settings()) {
			if (s instanceof TextSetting t) {
				EditBox box = new EditBox(font, 0, -100, (int) FIELD_W - 12, 10, Component.literal(t.name()));
				box.setBordered(false);
				box.setMaxLength(t.maxLength());
				box.setValue(t.get());
				box.setTextColor(0xFFFFFFFF);
				box.setResponder(t::set);
				boxes.put(t, box);
				addWidget.accept(box);
			}
		}
	}

	Module module() {
		return module;
	}

	void dispose() {
		for (EditBox box : boxes.values()) removeWidget.accept(box);
		boxes.clear();
	}

	void refreshTextBoxes() {
		for (var e : boxes.entrySet()) e.getValue().setValue(e.getKey().get());
	}

	private List<Setting<?>> visible() {
		List<Setting<?>> out = new ArrayList<>();
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

	void render(GuiGraphicsExtractor g, float x, float y, float w, float h, int mouseX, int mouseY, Theme t) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		scroll = Ui.approach(scroll, scrollTarget, 18f);
		g.enableScissor(Math.round(x) - 2, Math.round(y), Math.round(x + w) + 2, Math.round(y + h));
		float by = y - scroll;
		boolean mouseIn = Ui.inside(mouseX, mouseY, x, y, w, h);
		for (Setting<?> s : visible()) {
			float bh = barHeight(s);
			if (by + bh >= y && by <= y + h) drawBar(g, s, x, by, w, bh, mouseIn ? mouseX : -1, mouseY, t);
			by += bh + GAP;
		}
		g.disableScissor();
		for (var e : boxes.entrySet()) {
			EditBox box = e.getValue();
			box.visible = e.getKey().isVisible() && box.getY() >= y && box.getY() + 10 <= y + h;
		}
		float max = maxScroll();
		if (max > 0) {
			float thumb = Math.max(16, h * h / (h + max));
			float ty = y + (h - thumb) * (scroll / max);
			Ui.rect(g, x + w + 4, ty, 2, thumb, 1, Theme.alpha(t.onSurface(), 0.25f));
		}
	}

	private String summary(Setting<?> s) {
		return switch (s) {
			case BooleanSetting b -> b.on() ? "Enabled" : "Disabled";
			case NumberSetting n -> n.display();
			case ModeSetting m -> m.get();
			case ColorSetting c -> c.chroma() ? "Chroma" : String.format(Locale.ROOT, "#%06X", c.get() & 0xFFFFFF)
				+ (c.allowsAlpha() ? "  ·  " + Math.round(c.alpha() / 2.55f) + "% opacity" : "");
			case TextSetting txt -> "Click the box to type";
			default -> "";
		};
	}

	private void drawBar(GuiGraphicsExtractor g, Setting<?> s, float x, float y, float w, float h, int mx, int my, Theme t) {
		boolean hover = Ui.inside(mx, my, x, y, w, h);
		Ui.rect(g, x, y, w, h, RADIUS, hover ? t.surfaceContainerLow() : t.surface());
		float rowMid = y + BAR_H / 2f;
		Ui.icon(g, Icons.of(s), x + 15, rowMid, t.onSurface(), Fonts.ICON_M);
		Ui.text(g, s.name(), x + 28, rowMid - 7.5f, t.onSurface(), Fonts.BODY);
		Ui.text(g, summary(s), x + 28, rowMid + 2.5f, t.onSurfaceVariant(), Fonts.SMALL);
		float right = x + w - 11;
		switch (s) {
			case BooleanSetting b -> drawSwitch(g, b, right - SWITCH_W, rowMid - SWITCH_H / 2f, t);
			case NumberSetting n -> drawSlider(g, n.fraction(), right - SLIDER_W, rowMid - 9.5f, SLIDER_W, t);
			case ModeSetting m -> drawCombo(g, m.get(), right, rowMid, mx, my, t);
			case TextSetting txt -> drawField(g, txt, right - FIELD_W, rowMid - FIELD_H / 2f, t);
			case ColorSetting c -> drawColor(g, c, x, y, right, mx, my, t);
			default -> {
			}
		}
	}

	private void drawSwitch(GuiGraphicsExtractor g, BooleanSetting b, float x, float y, Theme t) {
		float f = Ui.approach(switchAnim.getOrDefault(b, b.on() ? 1f : 0f), b.on() ? 1f : 0f, 16f);
		switchAnim.put(b, f);
		if (f < 0.5f) Ui.outlined(g, x, y, SWITCH_W, SWITCH_H, SWITCH_H / 2f, t.surfaceContainerHighest(), t.outlineVariant(), 2);
		else Ui.rect(g, x, y, SWITCH_W, SWITCH_H, SWITCH_H / 2f, t.primary());
		float cx = x + 8 + 10 * f;
		float radius = 4 + 2 * f;
		Ui.circle(g, cx, y + SWITCH_H / 2f, radius, f < 0.5f ? t.onSurfaceVariant() : t.onPrimary());
	}

	/** Material 3 "expressive" slider: tall thin handle splitting an active and an inactive track. */
	static void drawSlider(GuiGraphicsExtractor g, double fraction, float x, float y, float w, Theme t) {
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

	private void drawCombo(GuiGraphicsExtractor g, String value, float right, float mid, int mx, int my, Theme t) {
		float cw = comboWidth(value), x = right - cw, y = mid - 8;
		boolean hover = Ui.inside(mx, my, x, y, cw, 16);
		Ui.rect(g, x, y, cw, 16, 6, hover ? Theme.mix(t.primary(), 0xFFFFFFFF, 0.12f) : t.primary());
		Ui.icon(g, Icon.CHEVRON_LEFT, x + 8, mid, t.onPrimary(), Fonts.ICON_S);
		Ui.icon(g, Icon.CHEVRON_RIGHT, x + cw - 8, mid, t.onPrimary(), Fonts.ICON_S);
		Ui.textCentered(g, value, x + cw / 2f, mid, t.onPrimary(), Fonts.BODY);
	}

	private void drawField(GuiGraphicsExtractor g, TextSetting s, float x, float y, Theme t) {
		EditBox box = boxes.get(s);
		boolean focused = box != null && box.isFocused();
		Ui.outlined(g, x, y, FIELD_W, FIELD_H, 6, t.surfaceContainerHigh(), focused ? t.primary() : t.outlineVariant(), focused ? 2 : 1);
		if (box != null) {
			box.setX(Math.round(x + 6));
			box.setY(Math.round(y + 4));
		}
	}

	private void drawColor(GuiGraphicsExtractor g, ColorSetting c, float x, float y, float right, int mx, int my, Theme t) {
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
		switch (s) {
			case BooleanSetting b -> b.toggle();
			case NumberSetting n -> {
				float sx = right - SLIDER_W;
				if (Ui.inside(mx, my, sx - 6, mid - 12, SLIDER_W + 12, 24)) {
					draggingSlider = n;
					dragX = sx;
					dragW = SLIDER_W;
					n.setFraction((mx - sx - 3) / (SLIDER_W - 6));
				}
			}
			case ModeSetting m -> {
				float cw = comboWidth(m.get()), cx = right - cw;
				if (Ui.inside(mx, my, cx, mid - 8, cw / 2f, 16) || button == InputConstants.MOUSE_BUTTON_RIGHT) m.cycle(-1);
				else m.cycle(1);
			}
			case ColorSetting c -> clickColor(c, mx, my, by, right);
			default -> {
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
