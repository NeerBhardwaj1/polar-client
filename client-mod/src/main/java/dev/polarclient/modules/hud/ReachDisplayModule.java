package dev.polarclient.modules.hud;

import dev.polarclient.module.Category;
import dev.polarclient.module.TextHudModule;
import dev.polarclient.util.CombatTracker;

import java.util.List;
import java.util.Locale;

public class ReachDisplayModule extends TextHudModule {
	public ReachDisplayModule() {
		super("reach_display", "Reach Display", "Distance of your last hit, in blocks.", Category.COMBAT, false, 214, 150);
	}

	@Override
	public boolean hasContent() {
		return CombatTracker.lastReach() >= 0 && CombatTracker.msSinceLastHit() < 3000;
	}

	@Override
	protected List<String> lines(boolean preview) {
		double r = CombatTracker.lastReach();
		return List.of(r < 0 ? "2.87 blocks" : String.format(Locale.ROOT, "%.2f blocks", r));
	}
}
