package dev.polarclient.modules;

import dev.polarclient.PolarKeys;
import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ModeSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/** Look around your character without changing where you're facing or walking (Left Alt by default). */
public class FreelookModule extends Module {
	private final ModeSetting activation = add(new ModeSetting("activation", "Activation", "Hold", "Hold", "Toggle"));
	private final BooleanSetting thirdPerson = add(new BooleanSetting("third_person", "Switch to third person", true));
	private final BooleanSetting invertPitch = add(new BooleanSetting("invert_pitch", "Invert up/down", false));

	private boolean active;
	private boolean toggledOn;
	private float yaw, pitch;
	private CameraType previous;

	public FreelookModule() {
		super("freelook", "Freelook", "Hold Left Alt to look around without turning. Some servers don't allow it.", Category.UTILITY, true);
	}

	@Override
	public void tick(Minecraft mc) {
		boolean want;
		if (activation.is("Toggle")) {
			while (PolarKeys.FREELOOK.consumeClick()) toggledOn = !toggledOn;
			want = toggledOn;
		} else {
			while (PolarKeys.FREELOOK.consumeClick()) {
				// clicks are only used in toggle mode
			}
			want = PolarKeys.FREELOOK.isDown();
		}
		want &= isEnabled() && mc.player != null;
		if (want && !active) start(mc);
		else if (!want && active) stop(mc);
	}

	@Override
	protected void onDisable() {
		toggledOn = false;
		if (active) stop(Minecraft.getInstance());
	}

	private void start(Minecraft mc) {
		active = true;
		yaw = mc.player.getYRot();
		pitch = mc.player.getXRot();
		if (thirdPerson.on() && mc.options.getCameraType().isFirstPerson()) {
			previous = mc.options.getCameraType();
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
	}

	private void stop(Minecraft mc) {
		active = false;
		toggledOn = false;
		if (previous != null) {
			mc.options.setCameraType(previous);
			previous = null;
		}
	}

	public boolean isActive() {
		return active;
	}

	/** Mouse movement redirected from the player to the camera (same maths as Entity#turn). */
	public void turn(double xo, double yo) {
		yaw += (float) xo * 0.15f;
		pitch = Mth.clamp(pitch + (float) (invertPitch.on() ? -yo : yo) * 0.15f, -90f, 90f);
	}

	public float yaw() {
		return yaw;
	}

	public float pitch() {
		return pitch;
	}
}
