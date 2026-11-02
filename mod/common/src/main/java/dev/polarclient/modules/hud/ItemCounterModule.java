package dev.polarclient.modules.hud;

import dev.polarclient.gfx.Gfx;
import dev.polarclient.module.Category;
import dev.polarclient.module.HudModule;
import dev.polarclient.platform.ItemKind;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ModeSetting;
import dev.polarclient.util.Draw;

public class ItemCounterModule extends HudModule {
	private final ModeSetting item = add(new ModeSetting("item", "Count", "Arrows", "Arrows", "Totems", "Ender Pearls", "Golden Apples"));
	private final BooleanSetting hideZero = add(new BooleanSetting("hide_zero", "Hide when you have none", true));

	public ItemCounterModule() {
		super("item_counter", "Item Counter", "Count arrows, pearls and gapples.", Category.COMBAT, false, 440, 200);
	}

	private ItemKind target() {
		String v = item.get();
		if (v.equals("Totems")) return ItemKind.TOTEM;
		if (v.equals("Ender Pearls")) return ItemKind.ENDER_PEARL;
		if (v.equals("Golden Apples")) return ItemKind.GOLDEN_APPLE;
		return ItemKind.ARROW;
	}

	private int count() {
		return p().hasPlayer() ? p().countItems(target()) : 0;
	}

	@Override
	public boolean hasContent() {
		return !hideZero.on() || count() > 0;
	}

	@Override
	public int width(boolean preview) {
		return 16 + 3 + Draw.width(String.valueOf(preview && count() == 0 ? 64 : count()));
	}

	@Override
	public int height(boolean preview) {
		return 16;
	}

	@Override
	protected void draw(Gfx g, boolean preview) {
		int n = count();
		if (preview && n == 0) n = 64;
		g.item(p().itemOf(target()), 0, 0);
		g.text(String.valueOf(n), 19, 4, 0xFFFFFFFF, true);
	}
}
