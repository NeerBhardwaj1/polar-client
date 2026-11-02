package dev.polarclient.modules.world;

import dev.polarclient.module.Category;
import dev.polarclient.module.TextHudModule;
import dev.polarclient.setting.TextSetting;

import java.util.List;
import java.util.Locale;

/** Distance and direction to a point you type in. */
public class DistanceModule extends TextHudModule {
	private final TextSetting target = add(new TextSetting("target", "Target (x z)", "0 0", 32));
	private static final String[] ARROWS = {"\u2191", "\u2197", "\u2192", "\u2198", "\u2193", "\u2199", "\u2190", "\u2196"};

	public DistanceModule() {
		super("distance_calculator", "Distance Calculator", "Distance to a point you set.", Category.WORLD, false, 4, 256);
	}

	private double[] point() {
		String[] parts = target.get().trim().replace(",", " ").split("\\s+");
		try {
			if (parts.length >= 3) return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[2])};
			if (parts.length == 2) return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
		} catch (NumberFormatException ignored) {
		}
		return null;
	}

	@Override
	protected List<String> lines(boolean preview) {
		double[] pt = point();
		if (pt == null) return one("Target: type x z in the settings");
		double x = p().playerX(), z = p().playerZ();
		double ddx = pt[0] - x, ddz = pt[1] - z;
		double dist = Math.sqrt(ddx * ddx + ddz * ddz);
		// angle of the target relative to where you're looking (Minecraft yaw: 0 = +Z, 90 = -X)
		double targetYaw = Math.toDegrees(Math.atan2(-ddx, ddz));
		double rel = ((targetYaw - p().playerYaw()) % 360 + 360 + 22.5) % 360;
		String arrow = ARROWS[(int) (rel / 45) % 8];
		return one(String.format(Locale.ROOT, "Target: %.0f m %s", dist, arrow));
	}
}
