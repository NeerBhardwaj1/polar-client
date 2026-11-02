package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.polarclient.Polar;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.tags.FluidTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clear Water: much thinner fog under water. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Inject(method = "setupFog", at = @At("TAIL"))
	private static void polar$clearWater(Camera camera, FogRenderer.FogMode mode, float distance, boolean thick, CallbackInfo ci) {
		if (Polar.modules() == null || !Polar.modules().clearWater.isEnabled()) return;
		if (camera.getFluidInCamera().is(FluidTags.WATER)) RenderSystem.fogDensity(1.2f / Math.max(32f, distance));
	}
}
