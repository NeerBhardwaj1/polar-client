package dev.polarclient.modules.hud;

import dev.polarclient.module.HudModule;
import dev.polarclient.setting.ColorSetting;
import dev.polarclient.util.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

public class CompassModule extends HudModule {
	private static final int W = 140, H = 16;
	private static final float PX_PER_DEG = 0.7f;
	// Minecraft yaw: 0 = south, 90 = west, 180 = north, 270 = east
	private static final String[] LABELS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

	private final ColorSetting accent = add(new ColorSetting("accent", "Marker color", 0xFF4FA3FF));

	public CompassModule() {
		super("compass", "Compass", "A heading strip like in battle-royale games.", false, 170, 4);
	}

	@Override
	public int width(boolean preview) {
		return W;
	}

	@Override
	public int height(boolean preview) {
		return H;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, boolean preview) {
		var p = Minecraft.getInstance().player;
		float yaw = p != null ? p.getYRot() : 180f;
		Draw.round(g, 0, 0, W, H, 3, 0x880B1426);
		g.enableScissor(0, 0, W, H);
		int center = W / 2;
		for (int a = 0; a < 360; a += 15) {
			float diff = Mth.wrapDegrees(a - yaw);
			int x = center + Math.round(diff * PX_PER_DEG);
			if (x < 2 || x > W - 2) continue;
			if (a % 45 == 0) {
				String label = LABELS[a / 45];
				int color = label.equals("N") ? 0xFFFF6B6B : label.length() == 1 ? 0xFFFFFFFF : 0xFFA9C5E8;
				g.text(Draw.font(), label, x - Draw.font().width(label) / 2, 4, color, true);
			} else {
				g.fill(x, 6, x + 1, 10, 0x88FFFFFF);
			}
		}
		g.disableScissor();
		g.fill(center, 0, center + 1, 3, accent.argb());
		g.fill(center, H - 3, center + 1, H, accent.argb());
	}
}
