package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fullbright: max brightness plus full night-vision lighting, without touching your gamma option. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void polar$fullbright(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
		if (Polar.modules() != null && Polar.modules().fullbright.isEnabled()) {
			renderState.brightness = 1.0f;
			renderState.nightVisionEffectIntensity = 1.0f;
		}
	}
}
