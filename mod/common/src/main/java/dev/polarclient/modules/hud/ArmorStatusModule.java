package dev.polarclient.modules.hud;

import dev.polarclient.gfx.Gfx;
import dev.polarclient.module.HudModule;
import dev.polarclient.platform.ItemView;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ModeSetting;
import dev.polarclient.util.Draw;

import java.util.List;

public class ArmorStatusModule extends HudModule {
	private final ModeSetting layout = add(new ModeSetting("layout", "Layout", "Vertical", "Vertical", "Horizontal"));
	private final ModeSetting durability = add(new ModeSetting("durability", "Durability", "Percent", "Percent", "Value", "Hidden"));
	private final BooleanSetting mainHand = add(new BooleanSetting("main_hand", "Include held item", true));
	private final BooleanSetting colorCoded = add(new BooleanSetting("color_coded", "Color by durability", true));

	public ArmorStatusModule() {
		super("armor_status", "Armor Status", "Armor and held item durability.", true, 440, 100);
	}

	private List<ItemView> items(boolean preview) {
		List<ItemView> out = p().armorAndHand(mainHand.on());
		if (out.isEmpty() && preview) out = p().previewArmor(mainHand.on());
		return out;
	}

	@Override
	public boolean hasContent() {
		return !items(false).isEmpty();
	}

	private String label(ItemView s) {
		if (durability.is("Hidden")) return "";
		if (s.damageable) {
			int left = s.maxDamage - s.damage;
			return durability.is("Percent") ? (left * 100 / Math.max(1, s.maxDamage)) + "%" : String.valueOf(left);
		}
		return s.count > 1 ? String.valueOf(s.count) : "";
	}

	private int labelColor(ItemView s) {
		if (!colorCoded.on() || !s.damageable) return 0xFFFFFFFF;
		float f = 1f - s.damage / (float) Math.max(1, s.maxDamage);
		return f > 0.6f ? 0xFF6EE7A0 : f > 0.3f ? 0xFFFFE066 : 0xFFFF6B6B;
	}

	private int cell(boolean preview) {
		int w = 0;
		for (ItemView s : items(preview)) w = Math.max(w, Draw.width(label(s)));
		return 16 + (w > 0 ? 3 + w : 0);
	}

	@Override
	public int width(boolean preview) {
		int n = Math.max(1, items(preview).size());
		return layout.is("Vertical") ? cell(preview) : n * cell(preview) + (n - 1) * 4;
	}

	@Override
	public int height(boolean preview) {
		int n = Math.max(1, items(preview).size());
		return layout.is("Vertical") ? n * 17 : 16;
	}

	@Override
	protected void draw(Gfx g, boolean preview) {
		int x = 0, y = 0, cell = cell(preview);
		for (ItemView s : items(preview)) {
			g.item(s, x, y);
			g.itemDecorations(s, x, y);
			String l = label(s);
			if (!l.isEmpty()) g.text(l, x + 19, y + 4, labelColor(s), true);
			if (layout.is("Vertical")) y += 17;
			else x += cell + 4;
		}
	}
}
