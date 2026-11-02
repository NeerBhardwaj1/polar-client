package dev.polarclient.modules;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.platform.PolarKey;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ModeSetting;

/** Look around your character without changing where you're facing or walking (Left Alt by default). */
public class FreelookModule extends Module {
	private final ModeSetting activation = add(new ModeSetting("activation", "Activation", "Hold", "Hold", "Toggle"));
	private final BooleanSetting thirdPerson = add(new BooleanSetting("third_person", "Switch to third person", true));
	private final BooleanSetting invertPitch = add(new BooleanSetting("invert_pitch", "Invert up/down", false));

	private boolean active;
	private boolean toggledOn;
	private float yaw, pitch;
	private int previous = -1;

	public FreelookModule() {
		super("freelook", "Freelook", "Look around freely while moving.", Category.VISUAL, true);
	}

	@Override
	public void tick() {
		boolean want;
		if (activation.is("Toggle")) {
			while (p().consumePolarKey(PolarKey.FREELOOK)) toggledOn = !toggledOn;
			want = toggledOn;
		} else {
			while (p().consumePolarKey(PolarKey.FREELOOK)) {
				// clicks are only used in toggle mode
			}
			want = p().polarKeyDown(PolarKey.FREELOOK);
		}
		want &= isEnabled() && p().hasPlayer();
		if (want && !active) start();
		else if (!want && active) stop();
	}

	@Override
	protected void onDisable() {
		toggledOn = false;
		if (active) stop();
	}

	private void start() {
		active = true;
		yaw = p().playerYaw();
		pitch = p().playerPitch();
		if (thirdPerson.on() && p().perspective() == 0) {
			previous = 0;
			p().setPerspective(1);
		}
	}

	private void stop() {
		active = false;
		toggledOn = false;
		if (previous >= 0) {
			p().setPerspective(previous);
			previous = -1;
		}
	}

	public boolean isActive() {
		return active;
	}

	/** Mouse movement redirected from the player to the camera (same maths as Entity#turn). */
	public void turn(double xo, double yo) {
		yaw += (float) xo * 0.15f;
		pitch = Math.max(-90f, Math.min(90f, pitch + (float) (invertPitch.on() ? -yo : yo) * 0.15f));
	}

	public float yaw() {
		return yaw;
	}

	public float pitch() {
		return pitch;
	}
}
