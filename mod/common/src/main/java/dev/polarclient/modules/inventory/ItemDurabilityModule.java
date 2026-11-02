package dev.polarclient.modules.inventory;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.platform.ItemView;
import dev.polarclient.setting.ModeSetting;

import java.util.Locale;

/** "Durability: 1290 / 1561" in the tooltip of every tool and piece of armour. */
public class ItemDurabilityModule extends Module {
	public final ModeSetting format = add(new ModeSetting("format", "Show", "Both", "Numbers", "Percent", "Both"));

	public ItemDurabilityModule() {
		super("item_durability", "Exact Durability", "Shows exact durability in item tooltips, coloured by how worn the item is.", Category.UTILITY, true);
	}

	/** The tooltip line for an item, or null. {@code advanced} = the game already shows durability (F3 + H). */
	public String line(ItemView item, boolean advanced) {
		if (!isEnabled() || item == null || !item.damageable || item.maxDamage <= 0 || advanced) return null;
		int left = item.maxDamage - item.damage;
		int pct = Math.round(left * 100f / item.maxDamage);
		switch (format.get()) {
			case "Numbers": return "Durability: " + left + " / " + item.maxDamage;
			case "Percent": return "Durability: " + pct + "%";
			default: return String.format(Locale.ROOT, "Durability: %d / %d (%d%%)", left, item.maxDamage, pct);
		}
	}

	/** RGB for the line: green when new, yellow when half worn, red when nearly broken. */
	public static int color(ItemView item) {
		float f = (item.maxDamage - item.damage) / (float) item.maxDamage;
		return f > 0.6f ? 0x6EE7A0 : f > 0.25f ? 0xFFE066 : 0xFF6B6B;
	}
}
