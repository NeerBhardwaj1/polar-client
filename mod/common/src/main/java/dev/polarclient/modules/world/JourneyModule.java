package dev.polarclient.modules.world;

import dev.polarclient.module.Category;
import dev.polarclient.module.TextHudModule;

import java.util.List;
import java.util.Locale;

/** Distance travelled this session. */
public class JourneyModule extends TextHudModule {
	private double total, lx, lz;
	private boolean tracking;

	public JourneyModule() {
		super("journey_tracker", "Journey Tracker", "Distance you've traveled this session.", Category.WORLD, false, 4, 232);
	}

	@Override
	public void tick() {
		if (!p().hasPlayer()) {
			tracking = false;
			if (!p().inWorld()) total = 0;
			return;
		}
		double x = p().playerX(), z = p().playerZ();
		if (tracking) {
			double d = Math.sqrt((x - lx) * (x - lx) + (z - lz) * (z - lz));
			if (d < 20) total += d; // ignore teleports and respawns
		}
		lx = x;
		lz = z;
		tracking = true;
	}

	@Override
	protected List<String> lines(boolean preview) {
		double t = preview && total == 0 ? 1240 : total;
		return one(t >= 1000 ? String.format(Locale.ROOT, "Traveled: %.2f km", t / 1000) : String.format(Locale.ROOT, "Traveled: %.0f m", t));
	}
}
