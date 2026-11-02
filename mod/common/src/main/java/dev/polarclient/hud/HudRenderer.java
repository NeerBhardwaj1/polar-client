package dev.polarclient.hud;

import dev.polarclient.Polar;
import dev.polarclient.gfx.Gfx;
import dev.polarclient.gui.HudEditorView;
import dev.polarclient.module.HudModule;
import dev.polarclient.module.ModuleManager;
import dev.polarclient.modules.HudTweaksModule;
import dev.polarclient.platform.Platform;
import dev.polarclient.module.Module;
import dev.polarclient.util.ClickTracker;
import dev.polarclient.util.FrameStats;
import dev.polarclient.util.Popups;

/**
 * Draws Polar's HUD mods. Each version calls {@link #render} from its HUD hook (just under chat), and asks the
 * {@code hide*} methods before drawing the vanilla pieces Polar can replace or hide.
 */
public final class HudRenderer {
	private HudRenderer() {
	}

	public static void render(Gfx g) {
		ClickTracker.update();
		FrameStats.frame();
		ModuleManager modules = Polar.modules();
		Platform p = Platform.get();
		if (modules == null || p == null) return;
		if (p.currentView() instanceof HudEditorView) return; // the editor draws previews itself
		if (modules.client.hideHudInDebug.on() && p.debugScreenOpen()) return;

		if (modules.lowHealth.isEnabled()) modules.lowHealth.render(g);
		int w = g.width(), h = g.height();
		for (HudModule module : modules.hud()) {
			if (module == modules.tabList) continue; // drawn where the game draws its player list
			if (module.isEnabled() && module.unavailableReason() == null) module.render(g, w, h, false);
		}
		for (Module module : modules.all()) {
			if (module.isEnabled() && !(module instanceof HudModule)) module.renderOverlay(g);
		}
		Popups.render(g);
	}

	/** Polar's Tab List, drawn in place of the game's player list (each version calls this from that spot). */
	public static void renderTabList(Gfx g) {
		ModuleManager m = Polar.modules();
		if (m == null || Platform.get() == null || !m.tabList.isEnabled()) return;
		if (m.client.hideHudInDebug.on() && Platform.get().debugScreenOpen()) return;
		m.tabList.render(g, g.width(), g.height(), false);
	}

	/** Draws Polar's crosshair instead of the vanilla one when it returns true. */
	public static boolean drawCrosshair(Gfx g) {
		ModuleManager m = Polar.modules();
		if (m == null || !m.crosshair.shouldDraw()) return false;
		m.crosshair.draw(g);
		return true;
	}

	private static HudTweaksModule tweaks() {
		return Polar.modules() == null ? null : Polar.modules().hudTweaks;
	}

	public static boolean hideVanillaEffects() {
		HudTweaksModule t = tweaks();
		return t != null && t.hides(t.hideVanillaEffects) && Polar.modules().byId("potion_effects").isEnabled();
	}

	/** The game's sidebar is hidden (HUD Tweaks) or drawn by Polar's Scoreboard mod instead. */
	public static boolean hideScoreboard() {
		HudTweaksModule t = tweaks();
		if (t == null) return false;
		return t.hides(t.hideScoreboard) || Polar.modules().scoreboard.isEnabled();
	}

	public static boolean hideBossBar() {
		HudTweaksModule t = tweaks();
		if (t == null) return false;
		return t.hides(t.hideBossBar) || Polar.modules().bossBar.isEnabled();
	}

	/** Polar's Tab List mod draws the player list instead of the game. */
	public static boolean hideTabList() {
		ModuleManager m = Polar.modules();
		return m != null && m.tabList.isEnabled();
	}

	public static boolean hideOverlays() {
		HudTweaksModule t = tweaks();
		return t != null && t.hides(t.noOverlays);
	}
}
