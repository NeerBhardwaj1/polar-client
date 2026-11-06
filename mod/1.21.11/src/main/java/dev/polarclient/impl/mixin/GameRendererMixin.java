package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import dev.polarclient.Polar;
import dev.polarclient.impl.MotionBlur;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom (scales the world FOV, not the hand's) and Motion Blur (drawn after the world, before the hand and HUD). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow @Final private Minecraft minecraft;
	@Shadow @Final private CrossFrameResourcePool resourcePool;
	@Shadow @Final private Camera mainCamera;

	@Shadow
	private float getFov(Camera camera, float partialTicks, boolean useFovSetting) {
		throw new AssertionError();
	}

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void polar$zoom(Camera camera, float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
		if (Polar.modules() == null || !useFovSetting) return;
		cir.setReturnValue(Polar.modules().zoom.modifyFov(cir.getReturnValueF()));
	}

	@Inject(method = "renderLevel", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
		target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"))
	private void polar$motionBlur(DeltaTracker deltaTracker, CallbackInfo ci) {
		float fov = this.getFov(this.mainCamera, deltaTracker.getGameTimeDeltaPartialTick(true), true);
		MotionBlur.render(this.minecraft, this.resourcePool, this.mainCamera, fov);
	}
}
