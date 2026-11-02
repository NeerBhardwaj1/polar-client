package dev.polarclient.modules;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.ModeSetting;
import dev.polarclient.setting.NumberSetting;
import dev.polarclient.util.CombatTracker;

/** Plays a sound when you hit something. */
public class HitSoundModule extends Module {
	private final ModeSetting sound = add(new ModeSetting("sound", "Sound", "Orb", "Orb", "Pling", "Click"));
	private final NumberSetting pitch = add(new NumberSetting("pitch", "Pitch", 1.0, 0.5, 2.0, 0.05, "x"));
	private long lastSeen;

	public HitSoundModule() {
		super("hit_sound", "Hit Sound", "Play a sound when you hit.", Category.COMBAT, false);
	}

	@Override
	public void tick() {
		long ms = CombatTracker.msSinceLastHit();
		long hitAt = System.currentTimeMillis() - ms;
		if (Math.abs(hitAt - lastSeen) > 5 && ms < 200) {
			lastSeen = hitAt;
			if (isEnabled()) p().playAlert(sound.is("Orb") ? 0 : sound.is("Pling") ? 1 : 2, pitch.getFloat());
		}
	}
}
