package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.polarclient.hud.HudRenderer;
import dev.polarclient.impl.McGfx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.Window;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Polar's HUD (drawn just under chat), the custom crosshair and the vanilla pieces it can hide. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;render(I)V"))
	private void polar$hud(float partialTicks, CallbackInfo ci) {
		// chat is drawn moved to the bottom of the screen; undo that for the HUD
		int shift = new Window(MinecraftClient.getInstance()).getHeight() - 48;
		GlStateManager.pushMatrix();
		GlStateManager.translate(0, -shift, 0);
		McGfx g = McGfx.begin();
		HudRenderer.render(g);
		g.end();
		GlStateManager.popMatrix();
		GlStateManager.enableBlend();
		GlStateManager.blendFuncSeparate(770, 771, 1, 0);
		GlStateManager.disableAlphaTest();
	}

	/** The vanilla crosshair is drawn right after this check; Polar's replaces it when it's on. */
	@Inject(method = "showCrosshair", at = @At("RETURN"), cancellable = true)
	private void polar$crosshair(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) return;
		McGfx g = McGfx.begin();
		boolean drawn = HudRenderer.drawCrosshair(g);
		g.end();
		if (drawn) {
			MinecraftClient.getInstance().getTextureManager().bindTexture(DrawableHelper.GUI_ICONS_TEXTURE);
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "renderScoreboardObjective", at = @At("HEAD"), cancellable = true)
	private void polar$scoreboard(ScoreboardObjective objective, Window window, CallbackInfo ci) {
		if (HudRenderer.hideScoreboard()) ci.cancel();
	}

	@Inject(method = "renderBossBar", at = @At("HEAD"), cancellable = true)
	private void polar$bossBar(CallbackInfo ci) {
		if (!HudRenderer.hideBossBar()) return;
		ci.cancel();
		// the game counts the bar down while drawing it; keep that going so it still disappears
		if (BossBar.name != null && BossBar.framesToLive > 0) BossBar.framesToLive--;
	}

	@Inject(method = "renderPumpkinBlur", at = @At("HEAD"), cancellable = true)
	private void polar$pumpkin(Window window, CallbackInfo ci) {
		if (HudRenderer.hideOverlays()) ci.cancel();
	}

	@Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
	private void polar$vignette(float brightness, Window window, CallbackInfo ci) {
		if (HudRenderer.hideOverlays()) ci.cancel();
	}

	@Inject(method = "renderNausea", at = @At("HEAD"), cancellable = true)
	private void polar$portal(float strength, Window window, CallbackInfo ci) {
		if (HudRenderer.hideOverlays()) ci.cancel();
	}
}
