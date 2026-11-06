package dev.polarclient.compat;

import dev.polarclient.Polar;
import dev.polarclient.module.ModuleManager;

import java.util.function.BooleanSupplier;

/**
 * BadOptimizations (in the launcher's performance pack) keeps the lightmap from one tick to the next and only redoes
 * it when it sees something change. Polar's Fullbright and Time &amp; Weather change it in ways it can't see, so this
 * tells it: true (redo it now) right after either one changes. It asks at most once a tick. Named in fabric.mod.json
 * under "badoptimizations:cache_hooks" (as the lightmap hook); never loaded without BadOptimizations.
 */
public final class BadOptimizationsHook implements BooleanSupplier {
	private long last = Long.MIN_VALUE;

	public BadOptimizationsHook() {
	}

	@Override
	public boolean getAsBoolean() {
		ModuleManager m = Polar.modules();
		if (m == null) return false;
		long now = m.fullbright.isEnabled() ? 1 : 0;
		now = now * 31 + m.timeWeather.dayTime();
		now = now * 31 + Float.floatToIntBits(m.timeWeather.rain(-1f));
		now = now * 31 + Float.floatToIntBits(m.timeWeather.thunder(-1f));
		if (now == last) return false;
		last = now;
		return true;
	}
}
