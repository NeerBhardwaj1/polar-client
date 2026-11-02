package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.polarclient.Polar;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Low Fire: the burning overlay drawn lower on the screen. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
	@Inject(method = "renderFireOverlay", at = @At("HEAD"))
	private void polar$lowerFire(float tickDelta, CallbackInfo ci) {
		GlStateManager.pushMatrix();
		if (Polar.modules() != null && Polar.modules().lowFire.isEnabled()) GlStateManager.translate(0f, -Polar.modules().lowFire.offset(), 0f);
	}

	@Inject(method = "renderFireOverlay", at = @At("RETURN"))
	private void polar$lowerFireEnd(float tickDelta, CallbackInfo ci) {
		GlStateManager.popMatrix();
	}
}
