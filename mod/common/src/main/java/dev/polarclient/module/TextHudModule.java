package dev.polarclient.module;

import dev.polarclient.Polar;
import dev.polarclient.gfx.Gfx;
import dev.polarclient.gfx.UiFont;
import dev.polarclient.platform.Platform;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ColorSetting;
import dev.polarclient.setting.ModeSetting;
import dev.polarclient.util.Draw;

import java.util.ArrayList;
import java.util.List;

/** A HUD element made of one or more lines of text in an optional box — the classic "Lunar" look. */
public abstract class TextHudModule extends HudModule {
	protected static final int PAD_X = 5, PAD_Y = 4, LINE_GAP = 2, MIN_BOX_W = 48;

	protected final ColorSetting textColor;
	protected final BooleanSetting shadow;
	protected final BooleanSetting background;
	protected final ColorSetting backgroundColor;
	protected final ModeSetting brackets;

	protected TextHudModule(String id, String name, String description, boolean enabledByDefault, double refX, double refY) {
		this(id, name, description, Category.HUD, enabledByDefault, refX, refY);
	}

	protected TextHudModule(String id, String name, String description, Category category, boolean enabledByDefault, double refX, double refY) {
		super(id, name, description, category, enabledByDefault, refX, refY);
		textColor = add(new ColorSetting("text_color", "Text color", 0xFFFFFFFF));
		shadow = add(new BooleanSetting("shadow", "Text shadow", true));
		background = add(new BooleanSetting("background", "Background", true));
		backgroundColor = add(new ColorSetting("background_color", "Background color", 0x880B1426, true));
		backgroundColor.visibleWhen(() -> background.on());
		brackets = add(new ModeSetting("brackets", "Brackets", "None", "None", "[ ]", "( )"));
	}

	/** Lines to show. {@code preview} = sample values for the HUD editor when there's no live data. */
	protected abstract List<String> lines(boolean preview);

	private List<String> decorated(boolean preview) {
		List<String> raw = lines(preview);
		if (brackets.is("None")) return raw;
		String open = brackets.is("[ ]") ? "[" : "(";
		String close = brackets.is("[ ]") ? "]" : ")";
		List<String> out = new ArrayList<String>(raw.size());
		for (String l : raw) out.add(open + l + close);
		return out;
	}

	/** Custom Font mod: HUD text in Polar's smooth font instead of Minecraft's. */
	protected static boolean smoothFont() {
		ModuleManager m = Polar.modules();
		return m != null && m.customFont.isEnabled();
	}

	protected static int textWidth(String s) {
		return smoothFont() ? Platform.get().textWidth(s, UiFont.BODY) : Draw.width(s);
	}

	protected static void drawText(Gfx g, String s, int x, int y, int color, boolean shadow) {
		if (smoothFont()) g.text(s, x, y + 1, color, UiFont.BODY, shadow);
		else g.text(s, x, y, color, shadow);
	}

	@Override
	public int width(boolean preview) {
		int w = 0;
		for (String l : decorated(preview)) w = Math.max(w, textWidth(l));
		return background.on() ? Math.max(MIN_BOX_W, w + PAD_X * 2) : w;
	}

	@Override
	public int height(boolean preview) {
		int n = Math.max(1, decorated(preview).size());
		int text = n * Draw.lineHeight() + (n - 1) * LINE_GAP - 1;
		return background.on() ? text + PAD_Y * 2 : text;
	}

	@Override
	protected void draw(Gfx g, boolean preview) {
		List<String> ls = decorated(preview);
		int w = width(preview), h = height(preview);
		if (background.on()) Draw.round(g, 0, 0, w, h, 3, backgroundColor.argb());
		int y = background.on() ? PAD_Y : 0;
		for (int i = 0; i < ls.size(); i++) {
			String l = ls.get(i);
			int x = background.on() ? (ls.size() == 1 ? (w - textWidth(l)) / 2 : PAD_X) : 0;
			drawText(g, l, x, y, textColor.argb(i * 0.08f), shadow.on());
			y += Draw.lineHeight() + LINE_GAP;
		}
	}

	/** One-line helper for subclasses. */
	protected static List<String> one(String line) {
		List<String> l = new ArrayList<String>(1);
		l.add(line);
		return l;
	}
}
