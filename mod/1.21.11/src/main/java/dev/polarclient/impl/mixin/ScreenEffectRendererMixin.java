package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.polarclient.Polar;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Low Fire: the burning overlay drawn lower on the screen. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	@Inject(method = "renderFire", at = @At("HEAD"))
	private static void polar$lowerFire(PoseStack poseStack, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
		poseStack.pushPose();
		if (Polar.modules() != null && Polar.modules().lowFire.isEnabled()) poseStack.translate(0f, -Polar.modules().lowFire.offset(), 0f);
	}

	@Inject(method = "renderFire", at = @At("RETURN"))
	private static void polar$lowerFireEnd(PoseStack poseStack, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
		poseStack.popPose();
	}
}
