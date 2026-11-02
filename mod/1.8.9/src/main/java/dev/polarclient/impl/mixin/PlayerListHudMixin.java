package dev.polarclient.impl.mixin;

import dev.polarclient.hud.HudRenderer;
import dev.polarclient.impl.McGfx;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Polar's Tab List draws instead of the game's player list. */
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
}
