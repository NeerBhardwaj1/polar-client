package dev.polarclient.modules.hud;

import dev.polarclient.module.Category;
import dev.polarclient.module.TextHudModule;

import java.util.List;

/** Deaths this session. */
public class DeathCounterModule extends TextHudModule {
	private int deaths;
	private boolean wasDead;

	public DeathCounterModule() {
		super("death_counter", "Death Counter", "Track deaths in this session.", Category.COMBAT, false, 4, 192);
	}

	@Override
	public void tick() {
		if (!p().inWorld()) {
			deaths = 0;
			wasDead = false;
			return;
		}
		boolean dead = p().hasPlayer() && p().health() <= 0;
		if (dead && !wasDead) deaths++;
		wasDead = dead;
	}

	@Override
	protected List<String> lines(boolean preview) {
		return one("Deaths: " + (preview && deaths == 0 ? 1 : deaths));
	}
}
