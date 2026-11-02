package dev.polarclient.modules.inventory;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.platform.Platform;

/** Hover a shulker box in your inventory to see what's inside. */
public class ShulkerPreviewModule extends Module {
	public ShulkerPreviewModule() {
		super("shulker_preview", "Shulker Preview", "Hover a shulker box in any inventory to see its items.", Category.UTILITY, true);
	}

	@Override
	public String unavailableReason() {
		return Platform.get() != null && "1.8.9".equals(Platform.get().minecraftVersion()) ? "Shulker boxes don't exist in 1.8.9." : null;
	}
}
