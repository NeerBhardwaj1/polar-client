package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.polarclient.hud.HudRenderer;
import dev.polarclient.impl.McGfx;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Polar's Tab List draws instead of the game's player list. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void polar$tabList(PoseStack pose, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
		if (!HudRenderer.hideTabList()) return;
		ci.cancel();
		McGfx g = McGfx.of(pose);
		HudRenderer.renderTabList(g);
		g.end();
	}
}
