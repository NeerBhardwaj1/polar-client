package dev.polarclient.modules.hud;

import dev.polarclient.module.TextHudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

public class SpeedModule extends TextHudModule {
	public SpeedModule() {
		super("speed", "Speed", "Horizontal speed in blocks per second.", false, 4, 230);
	}

	@Override
	protected List<String> lines(boolean preview) {
		var p = Minecraft.getInstance().player;
		if (p == null) return List.of("4.32 m/s");
		Vec3 v = p.getDeltaMovement();
		double bps = Math.sqrt(v.x * v.x + v.z * v.z) * 20.0;
		return List.of(String.format(Locale.ROOT, "%.2f m/s", bps));
	}
}
