package dev.polarclient.modules;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.BooleanSetting;

/** Hide vanilla HUD pieces (applied by wrapping the vanilla HUD layers in HudRenderer). */
public class HudTweaksModule extends Module {
	public final BooleanSetting hideVanillaEffects = add(new BooleanSetting("hide_vanilla_effects", "Hide vanilla effect icons", true));
	public final BooleanSetting hideScoreboard = add(new BooleanSetting("hide_scoreboard", "Hide scoreboard", false));
	public final BooleanSetting hideBossBar = add(new BooleanSetting("hide_boss_bar", "Hide boss bars", false));
	public final BooleanSetting noOverlays = add(new BooleanSetting("no_overlays", "No pumpkin, vignette or portal overlays", false));

	public HudTweaksModule() {
		super("hud_tweaks", "HUD Tweaks", "Modify and customize your HUD.", Category.HUD, true);
	}

	public boolean hides(BooleanSetting setting) {
		return isEnabled() && setting.on();
	}
}
