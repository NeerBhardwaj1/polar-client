package dev.polarclient.modules.hud;

import dev.polarclient.gfx.Gfx;
import dev.polarclient.module.Category;
import dev.polarclient.module.HudModule;
import dev.polarclient.util.Draw;
import dev.polarclient.platform.ItemView;

/** The item in your hand with its name and count. */
public class HeldItemModule extends HudModule {
	public HeldItemModule() {
		super("held_item", "Held Item Display", "Show your selected item.", Category.HUD, false, 214, 200);
	}

	private String label(boolean preview) {
		ItemView it = p().heldItem();
		if (it == null) return preview ? "Diamond Sword" : "";
		String name = p().itemName(it);
		return it.count > 1 ? name + " x" + it.count : name;
	}

	@Override
	public int width(boolean preview) {
		return 22 + Draw.width(label(preview));
	}

	@Override
	public int height(boolean preview) {
		return 18;
	}

	@Override
	public boolean hasContent() {
		return p().heldItem() != null;
	}

	@Override
	protected void draw(Gfx g, boolean preview) {
		ItemView it = p().heldItem();
		if (it == null && preview) it = p().previewArmor(true).isEmpty() ? null : p().previewArmor(true).get(p().previewArmor(true).size() - 1);
		Draw.round(g, 0, 0, width(preview), 18, 3, 0x880B1426);
		if (it != null) g.item(it, 1, 1);
		g.text(label(preview), 20, 5, 0xFFFFFFFF, true);
	}
}
