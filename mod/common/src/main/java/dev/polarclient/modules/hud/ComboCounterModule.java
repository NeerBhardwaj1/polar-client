package dev.polarclient.modules.hud;

import dev.polarclient.module.Category;
import dev.polarclient.module.TextHudModule;
import dev.polarclient.util.CombatTracker;

import java.util.List;

public class ComboCounterModule extends TextHudModule {
	public ComboCounterModule() {
		super("combo_counter", "Combo Counter", "Track hits in a row.", Category.COMBAT, false, 214, 166);
	}

	@Override
	public boolean hasContent() {
		return CombatTracker.combo() > 0;
	}

	@Override
	protected List<String> lines(boolean preview) {
		int c = CombatTracker.combo();
		return one((c == 0 ? 3 : c) + " Combo");
	}
}
