package dev.polarclient.modules.perf;

import dev.polarclient.module.Category;
import dev.polarclient.module.TextHudModule;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/** CPU use of the game process. */
public class CpuModule extends TextHudModule {
	private double last = -1;
	private long lastRead;

	public CpuModule() {
		super("cpu", "CPU Monitor", "Show the game's CPU usage.", Category.PERFORMANCE, false, 4, 168);
	}

	private double read() {
		long now = System.currentTimeMillis();
		if (now - lastRead < 1000 && last >= 0) return last;
		lastRead = now;
		try {
			Object os = ManagementFactory.getOperatingSystemMXBean();
			Method m = os.getClass().getMethod("getProcessCpuLoad");
			m.setAccessible(true);
			double v = ((Number) m.invoke(os)).doubleValue();
			last = v;
		} catch (Throwable t) {
			last = -1;
		}
		return last;
	}

	@Override
	protected List<String> lines(boolean preview) {
		double v = read();
		return one(v < 0 ? "CPU: n/a" : String.format(Locale.ROOT, "CPU: %.0f%%", v * 100));
	}
}
