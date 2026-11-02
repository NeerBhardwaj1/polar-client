package dev.polarclient.modules.visual;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.NumberSetting;

import java.util.Random;

/** Shows only part of the particles: more FPS in fights, explosions and farms. */
public class ParticlesModule extends Module {
	public final NumberSetting amount = add(new NumberSetting("amount", "Particles shown", 50, 0, 100, 5, "%"));
	private final Random random = new Random();

	public ParticlesModule() {
		super("particles", "Particles", "Shows fewer particles for more FPS (0% hides them all).", Category.VISUAL, false);
	}

	/** Whether to drop a new particle. */
	public boolean drop() {
		double keep = amount.get();
		return keep < 100 && (keep <= 0 || random.nextDouble() * 100 >= keep);
	}
}
