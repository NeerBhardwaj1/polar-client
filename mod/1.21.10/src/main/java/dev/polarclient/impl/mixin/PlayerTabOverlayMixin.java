package dev.polarclient.impl.mixin;

import dev.polarclient.cosmetics.Cosmetics;
import dev.polarclient.impl.CosmeticsRender;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Polar badge in front of the names of players on Polar in the player list. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void polar$badge(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		if (cir.getReturnValue() != null && Cosmetics.badge(info.getProfile().id())) cir.setReturnValue(CosmeticsRender.badged(cir.getReturnValue()));
	}
}
