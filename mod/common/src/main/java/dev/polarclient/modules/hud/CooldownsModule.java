package dev.polarclient.modules.hud;

import dev.polarclient.gfx.Gfx;
import dev.polarclient.module.Category;
import dev.polarclient.module.HudModule;
import dev.polarclient.util.Draw;
import dev.polarclient.platform.ItemKind;

/** Cooldowns on ender pearls, shields and chorus fruit. */
public class CooldownsModule extends HudModule {
	private static final ItemKind[] KINDS = {ItemKind.ENDER_PEARL, ItemKind.SHIELD, ItemKind.CHORUS_FRUIT};
	private static final String[] NAMES = {"Pearl", "Shield", "Chorus"};

	public CooldownsModule() {
		super("item_cooldowns", "Cooldowns", "Pearl, shield and chorus cooldowns.", Category.COMBAT, false, 300, 150);
	}

	@Override
	public String unavailableReason() {
		return p().hasAttackCooldown() ? null : "Item cooldowns came in Minecraft 1.9.";
	}

	private int active(boolean preview) {
		int n = 0;
		for (ItemKind k : KINDS) if (p().itemCooldown(k) > 0) n++;
		return n == 0 && preview ? 2 : n;
	}

	@Override
	public int width(boolean preview) {
		return 74;
	}

	@Override
	public int height(boolean preview) {
		return Math.max(1, active(preview)) * 12;
	}

	@Override
	public boolean hasContent() {
		return p().hasPlayer() && active(false) > 0;
	}

	@Override
	protected void draw(Gfx g, boolean preview) {
		int y = 0;
		for (int i = 0; i < KINDS.length; i++) {
			float f = p().itemCooldown(KINDS[i]);
			if (f <= 0 && preview && active(false) == 0 && i < 2) f = i == 0 ? 0.6f : 0.3f;
			if (f <= 0) continue;
			Draw.round(g, 0, y, 74, 11, 3, 0x880B1426);
			g.text(NAMES[i], 3, y + 2, 0xFFFFFFFF, true);
			Draw.round(g, 36, y + 4, 35, 3, 1, 0x55FFFFFF);
			Draw.round(g, 36, y + 4, Math.max(1, Math.round(35 * f)), 3, 1, 0xFF5CC8FF);
			y += 12;
		}
	}
}
