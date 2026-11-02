package dev.polarclient.modules.hud;

import dev.polarclient.module.Category;
import dev.polarclient.module.TextHudModule;
import dev.polarclient.platform.ItemView;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;

/** Warns when worn armour or your held item is nearly broken. */
public class DurabilityWarningModule extends TextHudModule {
	private final NumberSetting threshold = add(new NumberSetting("threshold", "Warn below", 10, 1, 50, 1, "%"));
	private final BooleanSetting sound = add(new BooleanSetting("sound", "Play a sound", true));
	private static final String[] SLOTS = {"Helmet", "Chestplate", "Leggings", "Boots", "Held item"};
	private boolean warned;

	public DurabilityWarningModule() {
		super("durability_warning", "Durability Warning", "Alert when gear is about to break.", Category.HUD, true, 214, 60);
	}

	private List<String> low() {
		List<String> out = new ArrayList<String>();
		if (!p().hasPlayer()) return out;
		List<ItemView> items = p().armorAndHand(true);
		for (int i = 0; i < items.size(); i++) {
			ItemView it = items.get(i);
			if (!it.damageable || it.maxDamage <= 0) continue;
			int pct = Math.round(100f * (it.maxDamage - it.damage) / it.maxDamage);
			if (pct <= threshold.getInt()) out.add(p().itemName(it) + " " + pct + "%");
		}
		return out;
	}

	@Override
	protected List<String> lines(boolean preview) {
		List<String> l = low();
		if (l.isEmpty() && preview) l.add("Diamond Helmet 7%");
		List<String> out = new ArrayList<String>();
		for (String s : l) out.add("\u26a0 " + s);
		return out;
	}

	@Override
	public boolean hasContent() {
		return !low().isEmpty() && (System.currentTimeMillis() / 500) % 2 == 0;
	}

	@Override
	public void tick() {
		if (!isEnabled() || !p().hasPlayer()) return;
		boolean any = !low().isEmpty();
		if (any && !warned && sound.on()) p().playAlert(2, 0.6f);
		warned = any;
	}
}
