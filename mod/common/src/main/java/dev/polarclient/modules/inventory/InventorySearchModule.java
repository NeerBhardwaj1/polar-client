package dev.polarclient.modules.inventory;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.BooleanSetting;

/** A search box on chests and inventories that dims every item that doesn't match. */
public class InventorySearchModule extends Module {
	public final BooleanSetting keep = add(new BooleanSetting("keep", "Keep search text", false));
	public final BooleanSetting insideShulkers = add(new BooleanSetting("inside_shulkers", "Search inside shulker boxes", true));

	public InventorySearchModule() {
		super("inventory_search", "Inventory Search", "Adds a search box to chests and inventories; items that don't match are dimmed.", Category.UTILITY, true);
	}
}
