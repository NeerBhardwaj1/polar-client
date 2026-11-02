package dev.polarclient.impl;

import dev.polarclient.Polar;
import dev.polarclient.hud.HudRenderer;
import dev.polarclient.modules.inventory.ItemDurabilityModule;
import dev.polarclient.platform.ItemView;
import dev.polarclient.util.CombatTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.BooleanSupplier;

/** Minecraft 26.3 entrypoint: hands the shared core a {@link McPlatform} and wires it into the game's events. */
public class PolarMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Polar.init(new McPlatform());
		Keys.register();
		registerHud();

		ClientTickEvents.END_CLIENT_TICK.register(mc -> Polar.tick());
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Polar.saveConfig());
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> Polar.onGameMessage(message.getString(), overlay));
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (Polar.modules() == null || stack.isEmpty()) return;
			ItemView view = McPlatform.view(stack);
			String line = Polar.modules().itemDurability.line(view, flag.isAdvanced());
			if (line != null) lines.add(Component.literal(line).withColor(ItemDurabilityModule.color(view)));
		});
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> Polar.onChatMessage(message.getString()));
		// fires on both client and integrated server, so only count your own hits
		AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
			if (player == Minecraft.getInstance().player && target instanceof LivingEntity) {
				CombatTracker.onHit(Math.sqrt(target.getBoundingBox().distanceToSqr(player.getEyePosition())));
			}
			return InteractionResult.PASS;
		});
	}

	private static void registerHud() {
		// Polar's layer sits just under chat, so chat and the tab list still draw on top of mods.
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(Polar.MOD_ID, "hud"),
			(g, dt) -> HudRenderer.render(McGfx.of(g)));
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (g, dt) -> {
			if (!HudRenderer.drawCrosshair(McGfx.of(g))) original.extractRenderState(g, dt);
		});
		hideWhen(VanillaHudElements.MOB_EFFECTS, HudRenderer::hideVanillaEffects);
		hideWhen(VanillaHudElements.SCOREBOARD, HudRenderer::hideScoreboard);
		hideWhen(VanillaHudElements.BOSS_BAR, HudRenderer::hideBossBar);
		hideWhen(VanillaHudElements.MISC_OVERLAYS, HudRenderer::hideOverlays);
		// Polar's Tab List takes the vanilla player list's place (above chat)
		HudElementRegistry.replaceElement(VanillaHudElements.PLAYER_LIST, original -> (HudElement) (g, dt) -> {
			if (HudRenderer.hideTabList()) HudRenderer.renderTabList(McGfx.of(g));
			else original.extractRenderState(g, dt);
		});
	}

	private static void hideWhen(Identifier layer, BooleanSupplier hidden) {
		HudElementRegistry.replaceElement(layer, original -> (HudElement) (g, dt) -> {
			if (!hidden.getAsBoolean()) original.extractRenderState(g, dt);
		});
	}
}
