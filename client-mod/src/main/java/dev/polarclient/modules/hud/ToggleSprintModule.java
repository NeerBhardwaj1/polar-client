package dev.polarclient.modules.hud;

import dev.polarclient.module.TextHudModule;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

import java.util.List;

/**
 * Toggle Sprint / Toggle Sneak using the game's own toggle options (server-safe),
 * plus an optional "always sprint" mode and a status line on the HUD.
 */
public class ToggleSprintModule extends TextHudModule {
	private final ModeSetting mode = add(new ModeSetting("mode", "Sprint mode", "Toggle", "Toggle", "Always"));
	private final BooleanSetting toggleSneak = add(new BooleanSetting("toggle_sneak", "Toggle sneak", false));
	private final BooleanSetting showText = add(new BooleanSetting("show_text", "Show status text", true));

	private Boolean savedToggleSprint, savedToggleSneak;

	public ToggleSprintModule() {
		super("toggle_sprint", "Toggle Sprint", "Tap sprint once to keep sprinting. Optional toggle sneak.", true, 4, 252);
	}

	@Override
	public void tick(Minecraft mc) {
		Options o = mc.options;
		if (o == null) return;
		if (isEnabled()) {
			if (savedToggleSprint == null) savedToggleSprint = o.toggleSprint().get();
			if (savedToggleSneak == null) savedToggleSneak = o.toggleCrouch().get();
			boolean wantToggle = mode.is("Toggle");
			if (o.toggleSprint().get() != wantToggle) o.toggleSprint().set(wantToggle);
			if (o.toggleCrouch().get() != toggleSneak.on()) o.toggleCrouch().set(toggleSneak.on());
			if (mode.is("Always") && mc.player != null && mc.gui.screen() == null) o.keySprint.setDown(true);
		} else {
			restore(o);
		}
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options != null) restore(mc.options);
	}

	private void restore(Options o) {
		if (savedToggleSprint != null) {
			o.toggleSprint().set(savedToggleSprint);
			savedToggleSprint = null;
		}
		if (savedToggleSneak != null) {
			o.toggleCrouch().set(savedToggleSneak);
			savedToggleSneak = null;
		}
	}

	private String status() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options == null) return null;
		Options o = mc.options;
		if (o.keyShift.isDown()) return o.toggleCrouch().get() ? "Sneaking (Toggled)" : "Sneaking (Key Held)";
		if (o.keySprint.isDown()) return mode.is("Always") ? "Sprinting (Always)" : "Sprinting (Toggled)";
		if (mc.player.isSprinting()) return "Sprinting (Vanilla)";
		return null;
	}

	@Override
	public boolean hasContent() {
		return showText.on() && status() != null;
	}

	@Override
	protected List<String> lines(boolean preview) {
		String s = status();
		return List.of(s != null ? s : "Sprinting (Toggled)");
	}
}
