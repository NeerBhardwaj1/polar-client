package dev.polarclient.modules.hud;

import dev.polarclient.module.Category;
import dev.polarclient.module.TextHudModule;
import dev.polarclient.platform.GameKey;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ModeSetting;

import java.util.List;

/**
 * Toggle Sprint / Toggle Sneak using the game's own toggle options where they exist (server-safe), or by holding the
 * keys down for you on versions without them (1.8.9), plus an optional "always sprint" mode and a HUD status line.
 */
public class ToggleSprintModule extends TextHudModule {
	private final ModeSetting mode = add(new ModeSetting("mode", "Sprint mode", "Toggle", "Toggle", "Always"));
	private final BooleanSetting toggleSneak = add(new BooleanSetting("toggle_sneak", "Toggle sneak", false));
	private final BooleanSetting showText = add(new BooleanSetting("show_text", "Show status text", true));

	private Boolean savedToggleSprint, savedToggleSneak;
	// emulated toggles (versions without the options)
	private boolean sprintToggled, sneakToggled, sprintWasDown, sneakWasDown, holding;

	public ToggleSprintModule() {
		super("toggle_sprint", "Toggle Sprint", "Toggle sprint and sneak.", Category.ACCESSIBILITY, true, 4, 252);
	}

	@Override
	public void tick() {
		if (!p().hasToggleOptions()) {
			tickEmulated();
			return;
		}
		if (isEnabled()) {
			if (savedToggleSprint == null) savedToggleSprint = p().toggleSprint();
			if (savedToggleSneak == null) savedToggleSneak = p().toggleSneak();
			boolean wantToggle = mode.is("Toggle");
			if (p().toggleSprint() != wantToggle) p().setToggleSprint(wantToggle);
			if (p().toggleSneak() != toggleSneak.on()) p().setToggleSneak(toggleSneak.on());
			if (mode.is("Always") && p().hasPlayer() && !p().screenOpen()) p().setKeyDown(GameKey.SPRINT, true);
		} else {
			restore();
		}
	}

	/** Toggle Sprint/Sneak by pressing the key once, for versions without the options. */
	private void tickEmulated() {
		boolean active = isEnabled() && p().hasPlayer() && !p().screenOpen();
		boolean sprintPhys = p().physicalKeyDown(GameKey.SPRINT), sneakPhys = p().physicalKeyDown(GameKey.SNEAK);
		if (active) {
			if (sprintPhys && !sprintWasDown) sprintToggled = !sprintToggled;
			if (sneakPhys && !sneakWasDown) sneakToggled = toggleSneak.on() && !sneakToggled;
			boolean sprint = mode.is("Always") || sprintToggled;
			p().setKeyDown(GameKey.SPRINT, sprint || sprintPhys);
			if (toggleSneak.on()) p().setKeyDown(GameKey.SNEAK, sneakToggled || sneakPhys);
			holding = true;
		} else if (holding) {
			p().setKeyDown(GameKey.SPRINT, sprintPhys);
			p().setKeyDown(GameKey.SNEAK, sneakPhys);
			holding = false;
			if (!isEnabled()) {
				sprintToggled = false;
				sneakToggled = false;
			}
		}
		sprintWasDown = sprintPhys;
		sneakWasDown = sneakPhys;
	}

	@Override
	protected void onDisable() {
		if (p() != null && p().hasToggleOptions()) restore();
	}

	private void restore() {
		if (savedToggleSprint != null) {
			p().setToggleSprint(savedToggleSprint);
			savedToggleSprint = null;
		}
		if (savedToggleSneak != null) {
			p().setToggleSneak(savedToggleSneak);
			savedToggleSneak = null;
		}
	}

	private String status() {
		if (!p().hasPlayer()) return null;
		boolean toggles = p().hasToggleOptions();
		if (p().keyDown(GameKey.SNEAK)) {
			boolean toggled = toggles ? p().toggleSneak() : sneakToggled;
			return toggled ? "Sneaking (Toggled)" : "Sneaking (Key Held)";
		}
		if (p().keyDown(GameKey.SPRINT)) return mode.is("Always") ? "Sprinting (Always)" : "Sprinting (Toggled)";
		if (p().sprinting()) return "Sprinting (Vanilla)";
		return null;
	}

	@Override
	public boolean hasContent() {
		return showText.on() && status() != null;
	}

	@Override
	protected List<String> lines(boolean preview) {
		String s = status();
		return one(s != null ? s : "Sprinting (Toggled)");
	}
}
