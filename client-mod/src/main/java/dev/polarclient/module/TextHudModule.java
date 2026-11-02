package dev.polarclient.module;

import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ColorSetting;
import dev.polarclient.setting.ModeSetting;
import dev.polarclient.util.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;

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
		backgroundColor.visibleWhen(background::on);
		brackets = add(new ModeSetting("brackets", "Brackets", "None", "None", "[ ]", "( )"));
	}

	/** Lines to show. {@code preview} = sample values for the HUD editor when there's no live data. */
	protected abstract List<String> lines(boolean preview);

	private List<String> decorated(boolean preview) {
		List<String> raw = lines(preview);
		if (brackets.is("None")) return raw;
		String open = brackets.is("[ ]") ? "[" : "(";
		String close = brackets.is("[ ]") ? "]" : ")";
		return raw.stream().map(l -> open + l + close).toList();
	}

	@Override
	public int width(boolean preview) {
		int w = 0;
		for (String l : decorated(preview)) w = Math.max(w, Draw.font().width(l));
		return background.on() ? Math.max(MIN_BOX_W, w + PAD_X * 2) : w;
	}

	@Override
	public int height(boolean preview) {
		int n = Math.max(1, decorated(preview).size());
		int text = n * Draw.font().lineHeight + (n - 1) * LINE_GAP - 1;
		return background.on() ? text + PAD_Y * 2 : text;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, boolean preview) {
		List<String> ls = decorated(preview);
		int w = width(preview), h = height(preview);
		if (background.on()) Draw.round(g, 0, 0, w, h, 3, backgroundColor.argb());
		int y = background.on() ? PAD_Y : 0;
		for (int i = 0; i < ls.size(); i++) {
			String l = ls.get(i);
			int x = background.on() ? (ls.size() == 1 ? (w - Draw.font().width(l)) / 2 : PAD_X) : 0;
			g.text(Draw.font(), l, x, y, textColor.argb(i * 0.08f), shadow.on());
			y += Draw.font().lineHeight + LINE_GAP;
		}
	}
}
