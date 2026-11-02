package dev.polarclient.modules;

import dev.polarclient.gfx.Gfx;
import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ColorSetting;
import dev.polarclient.setting.ModeSetting;
import dev.polarclient.setting.NumberSetting;

public class CustomCrosshairModule extends Module {
	private final ModeSetting style = add(new ModeSetting("style", "Style", "Cross", "Cross", "Plus + Dot", "Dot", "Circle", "T-Shape"));
	private final NumberSetting size = add(new NumberSetting("size", "Size", 4, 1, 12, 1));
	private final NumberSetting gap = add(new NumberSetting("gap", "Gap", 2, 0, 8, 1));
	private final NumberSetting thickness = add(new NumberSetting("thickness", "Thickness", 1, 1, 3, 1));
	private final ColorSetting color = add(new ColorSetting("color", "Color", 0xFF5CF2D6));
	private final BooleanSetting outline = add(new BooleanSetting("outline", "Black outline", true));
	private final BooleanSetting hitColorOn = add(new BooleanSetting("hit_color_on", "Change color when you hit", false));
	private final ColorSetting hitColor = add(new ColorSetting("hit_color", "Hit color", 0xFFFF5A5A));

	public CustomCrosshairModule() {
		super("custom_crosshair", "Custom Crosshair", "Use a custom crosshair style.", Category.VISUAL, false);
		hitColor.visibleWhen(() -> hitColorOn.on());
	}

	/** Whether to draw ours instead of the vanilla crosshair right now. */
	public boolean shouldDraw() {
		return isEnabled() && p().hasPlayer() && !p().spectator() && p().perspective() == 0;
	}

	public void draw(Gfx g) {
		int cx = g.width() / 2, cy = g.height() / 2;
		int c = hitColorOn.on() && dev.polarclient.util.CombatTracker.msSinceLastHit() < 250 ? hitColor.argb() : color.argb();
		int s = size.getInt(), gp = gap.getInt(), t = thickness.getInt();
		int half = t / 2;
		String st = style.get();
		if (st.equals("Dot")) {
			box(g, cx - half - 1, cy - half - 1, t + 2, t + 2, c);
		} else if (st.equals("Circle")) {
			circle(g, cx, cy, Math.max(2, s), c);
		} else {
			boolean top = !style.is("T-Shape");
			// left, right, (top), bottom arms
			box(g, cx - gp - s - half, cy - half, s, t, c);
			box(g, cx + gp + 1 - half, cy - half, s, t, c);
			if (top) box(g, cx - half, cy - gp - s - half, t, s, c);
			box(g, cx - half, cy + gp + 1 - half, t, s, c);
			if (style.is("Plus + Dot")) box(g, cx - half, cy - half, t, t, c);
		}
	}

	private void box(Gfx g, int x, int y, int w, int h, int c) {
		if (outline.on()) g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xC0000000);
		g.fill(x, y, x + w, y + h, c);
	}

	private void circle(Gfx g, int cx, int cy, int r, int c) {
		int steps = Math.max(16, r * 8);
		for (int i = 0; i < steps; i++) {
			double a = i * Math.PI * 2 / steps;
			int x = cx + (int) Math.round(Math.cos(a) * r);
			int y = cy + (int) Math.round(Math.sin(a) * r);
			if (outline.on()) g.fill(x - 1, y - 1, x + 2, y + 2, 0x80000000);
		}
		for (int i = 0; i < steps; i++) {
			double a = i * Math.PI * 2 / steps;
			int x = cx + (int) Math.round(Math.cos(a) * r);
			int y = cy + (int) Math.round(Math.sin(a) * r);
			g.fill(x, y, x + 1, y + 1, c);
		}
	}
}
