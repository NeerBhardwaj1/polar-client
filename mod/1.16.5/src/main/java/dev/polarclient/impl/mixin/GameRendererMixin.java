package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom (scales the world FOV) and No Hurt Cam (scales the 14 degree hurt shake; 1.16.5 has no Damage Tilt option). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void polar$zoom(Camera camera, float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
		if (Polar.modules() == null || !useFovSetting) return;
		cir.setReturnValue((double) Polar.modules().zoom.modifyFov((float) cir.getReturnValueD()));
	}

	@ModifyConstant(method = "bobHurt", constant = @Constant(floatValue = 14.0f))
	private float polar$hurtShake(float degrees) {
		return Polar.modules() == null ? degrees : degrees * Polar.modules().noHurtCam.shakeScale();
	}
}
