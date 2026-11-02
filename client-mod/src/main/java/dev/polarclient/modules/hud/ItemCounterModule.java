package dev.polarclient.modules.hud;

import dev.polarclient.module.Category;
import dev.polarclient.module.HudModule;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ModeSetting;
import dev.polarclient.util.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ItemCounterModule extends HudModule {
	private final ModeSetting item = add(new ModeSetting("item", "Count", "Arrows", "Arrows", "Totems", "Ender Pearls", "Golden Apples"));
	private final BooleanSetting hideZero = add(new BooleanSetting("hide_zero", "Hide when you have none", true));

	public ItemCounterModule() {
		super("item_counter", "Item Counter", "Counts arrows, totems, pearls or golden apples in your inventory.", Category.COMBAT, false, 440, 200);
	}

	private Item target() {
		return switch (item.get()) {
			case "Totems" -> Items.TOTEM_OF_UNDYING;
			case "Ender Pearls" -> Items.ENDER_PEARL;
			case "Golden Apples" -> Items.GOLDEN_APPLE;
			default -> Items.ARROW;
		};
	}

	private int count() {
		var p = Minecraft.getInstance().player;
		if (p == null) return 0;
		Inventory inv = p.getInventory();
		Item t = target();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.getItem() == t) n += s.getCount();
		}
		return n;
	}

	@Override
	public boolean hasContent() {
		return !hideZero.on() || count() > 0;
	}

	@Override
	public int width(boolean preview) {
		return 16 + 3 + Draw.font().width(String.valueOf(preview && count() == 0 ? 64 : count()));
	}

	@Override
	public int height(boolean preview) {
		return 16;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, boolean preview) {
		int n = count();
		if (preview && n == 0) n = 64;
		g.item(new ItemStack(target()), 0, 0);
		g.text(Draw.font(), String.valueOf(n), 19, 4, 0xFFFFFFFF, true);
	}
}
