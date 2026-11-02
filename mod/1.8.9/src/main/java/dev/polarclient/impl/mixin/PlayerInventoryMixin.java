package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scrolling while zoomed changes the zoom level instead of the hotbar slot. */
@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {
	@Inject(method = "scrollInHotbar", at = @At("HEAD"), cancellable = true)
	private void polar$zoomScroll(int amount, CallbackInfo ci) {
		if (Polar.modules() != null && Polar.modules().zoom.onScroll(amount)) ci.cancel();
	}
}
