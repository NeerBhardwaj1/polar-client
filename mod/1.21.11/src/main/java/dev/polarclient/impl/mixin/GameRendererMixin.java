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

/** Zoom (scales the world FOV, not the hand's) and Motion Blur (drawn over the world, before the HUD). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow @Final private Minecraft minecraft;
	@Shadow @Final private CrossFrameResourcePool resourcePool;

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void polar$zoom(Camera camera, float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
		if (Polar.modules() == null || !useFovSetting) return;
		cir.setReturnValue(Polar.modules().zoom.modifyFov(cir.getReturnValueF()));
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
	private void polar$motionBlur(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
		MotionBlur.render(this.minecraft, this.resourcePool);
	}
}
