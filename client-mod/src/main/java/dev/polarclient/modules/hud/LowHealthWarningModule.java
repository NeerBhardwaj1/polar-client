package dev.polarclient.modules.hud;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.ColorSetting;
import dev.polarclient.setting.NumberSetting;
import dev.polarclient.util.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Pulsing glow around the screen edges when your health is low. */
public class LowHealthWarningModule extends Module {
	private static final int STEPS = 14;
	private final NumberSetting threshold = add(new NumberSetting("threshold", "Hearts threshold", 3, 1, 10, 0.5, " hearts"));
	private final ColorSetting color = add(new ColorSetting("color", "Glow color", 0xFFFF3B3B));

	public LowHealthWarningModule() {
		super("low_health_warning", "Low Health Warning", "Red pulse around the screen when you're low on health.", Category.COMBAT, false);
	}

	public void render(GuiGraphicsExtractor g) {
		var p = Minecraft.getInstance().player;
		if (p == null || p.isCreative() || p.isSpectator()) return;
		float hearts = p.getHealth() / 2f;
		if (hearts > threshold.get() || hearts <= 0) return;
		float severity = 1f - hearts / (float) (double) threshold.get();
		float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 180.0);
		float strength = Math.min(1f, 0.35f + severity * 0.65f) * pulse;
		int w = g.guiWidth(), h = g.guiHeight();
		int base = color.argb();
		for (int i = 0; i < STEPS; i++) {
			int c = Draw.alpha(base, (int) (110 * strength * (1f - i / (float) STEPS)));
			g.fill(0, i, w, i + 1, c);
			g.fill(0, h - 1 - i, w, h - i, c);
			g.fill(i, STEPS, i + 1, h - STEPS, c);
			g.fill(w - 1 - i, STEPS, w - i, h - STEPS, c);
		}
	}
}
