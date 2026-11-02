package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.polarclient.Polar;
import dev.polarclient.hud.HudRenderer;
import dev.polarclient.impl.McGfx;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Polar's HUD (drawn just under chat), the custom crosshair, the vanilla pieces it can hide, and chat for AutoGG. */
@Mixin(Gui.class)
public abstract class GuiMixin {
	@Inject(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/components/ChatComponent;render(Lcom/mojang/blaze3d/vertex/PoseStack;I)V"))
	private void polar$hud(PoseStack pose, float partialTicks, CallbackInfo ci) {
		// chat is drawn moved to the bottom of the screen; undo that for the HUD
		int shift = Minecraft.getInstance().getWindow().getGuiScaledHeight() - 48;
		RenderSystem.pushMatrix();
		RenderSystem.translatef(0, -shift, 0);
		McGfx g = McGfx.of(pose);
		HudRenderer.render(g);
		g.end();
		RenderSystem.popMatrix();
	}

	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void polar$crosshair(PoseStack pose, CallbackInfo ci) {
		McGfx g = McGfx.of(pose);
		boolean drawn = HudRenderer.drawCrosshair(g);
		g.end();
		if (drawn) ci.cancel();
	}

	@Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
	private void polar$effects(PoseStack pose, CallbackInfo ci) {
		if (HudRenderer.hideVanillaEffects()) ci.cancel();
	}

	@Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
	private void polar$scoreboard(PoseStack pose, Objective objective, CallbackInfo ci) {
		if (HudRenderer.hideScoreboard()) ci.cancel();
	}

	@Inject(method = "renderPumpkin", at = @At("HEAD"), cancellable = true)
	private void polar$pumpkin(CallbackInfo ci) {
		if (HudRenderer.hideOverlays()) ci.cancel();
	}

	@Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
	private void polar$vignette(Entity entity, CallbackInfo ci) {
		if (HudRenderer.hideOverlays()) ci.cancel();
	}

	@Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
	private void polar$portal(float strength, CallbackInfo ci) {
		if (HudRenderer.hideOverlays()) ci.cancel();
	}

	/** Messages from the server itself (not players), like the game-over lines AutoGG listens for. */
	@Inject(method = "handleChat", at = @At("HEAD"))
	private void polar$chat(ChatType type, Component message, UUID sender, CallbackInfo ci) {
		if (Polar.modules() == null || type == ChatType.GAME_INFO) return;
		if (type == ChatType.SYSTEM || Util.NIL_UUID.equals(sender)) Polar.onGameMessage(message.getString(), false);
		else if (type == ChatType.CHAT) Polar.onChatMessage(message.getString());
	}
}
