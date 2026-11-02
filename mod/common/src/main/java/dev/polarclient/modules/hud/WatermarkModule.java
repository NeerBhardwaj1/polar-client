package dev.polarclient.modules.hud;

import dev.polarclient.Polar;
import dev.polarclient.gfx.Gfx;
import dev.polarclient.module.HudModule;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ColorSetting;
import dev.polarclient.util.Draw;

public class WatermarkModule extends HudModule {
	private final BooleanSetting showVersion = add(new BooleanSetting("version", "Show version", false));
	private final ColorSetting color = add(new ColorSetting("text_color", "Text color", 0xFFFFFFFF));
	private final BooleanSetting background = add(new BooleanSetting("background", "Background", true));

	public WatermarkModule() {
		super("watermark", "Watermark", "Show a custom watermark.", true, 4, 4);
	}

	private String label() {
		return showVersion.on() ? "Polar Client " + Polar.version() : "Polar Client";
	}

	@Override
	public int width(boolean preview) {
		return 4 + 12 + 4 + Draw.width(label()) + 6;
	}

	@Override
	public int height(boolean preview) {
		return 16;
	}

	@Override
	protected void draw(Gfx g, boolean preview) {
		int w = width(preview);
		if (background.on()) {
			Draw.round(g, 0, 0, w, 16, 3, 0x990B1426);
			g.fill(3, 15, w - 3, 16, Draw.alpha(Draw.ACCENT, 200));
		}
		Draw.texture(g, Draw.MARK, 3, 2, 12, 12);
		g.text(label(), 20, 4, color.argb(), true);
	}
}
