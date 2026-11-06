package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.polarclient.Polar;
import dev.polarclient.impl.MotionBlur;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Zoom (scales the world FOV), No Hurt Cam (scales the 14 degree hurt shake; 1.16.5 has no Damage Tilt option) and
 * Motion Blur (drawn after the world, before the hand and the HUD).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow @Final private Minecraft minecraft;
	@Shadow @Final private Camera mainCamera;

	@Shadow
	private double getFov(Camera camera, float partialTicks, boolean useFovSetting) {
		throw new AssertionError();
	}

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void polar$zoom(Camera camera, float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
		if (Polar.modules() == null || !useFovSetting) return;
		cir.setReturnValue((double) Polar.modules().zoom.modifyFov((float) cir.getReturnValueD()));
	}

	@ModifyConstant(method = "bobHurt", constant = @Constant(floatValue = 14.0f))
	private float polar$hurtShake(float degrees) {
		return Polar.modules() == null ? degrees : degrees * Polar.modules().noHurtCam.shakeScale();
	}

	@Inject(method = "renderLevel", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/Camera;F)V"))
	private void polar$motionBlur(float partialTicks, long nanoTime, PoseStack poseStack, CallbackInfo ci) {
		MotionBlur.render(this.minecraft, this.mainCamera, this.getFov(this.mainCamera, partialTicks, true), partialTicks);
	}
}
