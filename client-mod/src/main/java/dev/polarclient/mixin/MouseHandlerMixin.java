package dev.polarclient.mixin;

import dev.polarclient.PolarClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scrolling while zoomed changes the zoom level instead of the hotbar slot. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void polar$zoomScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
		if (PolarClient.modules() == null || Minecraft.getInstance().gui.screen() != null) return;
		if (PolarClient.modules().zoom.onScroll(yoffset)) ci.cancel();
	}
}
