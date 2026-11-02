package dev.polarclient.hud;

import dev.polarclient.PolarClient;
import dev.polarclient.gui.HudEditorScreen;
import dev.polarclient.module.HudModule;
import dev.polarclient.module.ModuleManager;
import dev.polarclient.modules.HudTweaksModule;
import dev.polarclient.util.ClickTracker;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Draws Polar HUD modules and wraps a few vanilla HUD layers so modules can replace or hide them. */
public final class HudRenderer {
	private HudRenderer() {
	}

	public static void register() {
		// Our layer sits just under chat, so chat and the tab list still draw on top of mods.
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(PolarClient.MOD_ID, "hud"), HudRenderer::render);

		ModuleManager m = PolarClient.modules();
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (g, dt) -> {
			if (m.crosshair.shouldDraw()) m.crosshair.draw(g);
			else original.extractRenderState(g, dt);
		});
		HudTweaksModule t = m.hudTweaks;
		hideWhen(VanillaHudElements.MOB_EFFECTS, () -> t.hides(t.hideVanillaEffects) && m.byId("potion_effects").isEnabled());
		hideWhen(VanillaHudElements.SCOREBOARD, () -> t.hides(t.hideScoreboard));
		hideWhen(VanillaHudElements.BOSS_BAR, () -> t.hides(t.hideBossBar));
		hideWhen(VanillaHudElements.MISC_OVERLAYS, () -> t.hides(t.noOverlays));
	}

	private static void hideWhen(Identifier layer, java.util.function.BooleanSupplier hidden) {
		HudElementRegistry.replaceElement(layer, original -> (HudElement) (g, dt) -> {
			if (!hidden.getAsBoolean()) original.extractRenderState(g, dt);
		});
	}

	private static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		ClickTracker.update();
		Minecraft mc = Minecraft.getInstance();
		ModuleManager modules = PolarClient.modules();
		if (mc.gui.screen() instanceof HudEditorScreen) return; // the editor draws previews itself
		if (modules.client.hideHudInDebug.on() && mc.getDebugOverlay().showDebugScreen()) return;

		if (modules.lowHealth.isEnabled()) modules.lowHealth.render(g);
		int w = g.guiWidth(), h = g.guiHeight();
		for (HudModule module : modules.hud()) {
			if (module.isEnabled()) module.render(g, w, h, false);
		}
	}
}
