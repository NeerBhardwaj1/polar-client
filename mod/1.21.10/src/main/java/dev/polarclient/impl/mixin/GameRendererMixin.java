package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: scales the world FOV (not the hand's). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void polar$zoom(Camera camera, float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
		if (Polar.modules() == null || !useFovSetting) return;
		cir.setReturnValue(Polar.modules().zoom.modifyFov(cir.getReturnValueF()));
	}
}
