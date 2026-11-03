package dev.polarclient.impl.mixin;

import dev.polarclient.cosmetics.Cosmetics;
import dev.polarclient.hud.HudRenderer;
import dev.polarclient.impl.CosmeticsRender;
import dev.polarclient.impl.McGfx;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Polar's Tab List draws instead of the game's player list; the Polar badge goes in front of Polar players' names. */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void polar$tabList(int width, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
		if (!HudRenderer.hideTabList()) return;
		ci.cancel();
		McGfx g = McGfx.begin();
		HudRenderer.renderTabList(g);
		g.end();
	}

	@Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
	private void polar$badge(PlayerListEntry entry, CallbackInfoReturnable<String> cir) {
		if (cir.getReturnValue() != null && entry.getProfile() != null && Cosmetics.badge(entry.getProfile().getId())) cir.setReturnValue(CosmeticsRender.BADGE + cir.getReturnValue());
	}
}
