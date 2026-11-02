package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Fullbright: full night-vision lighting and maximum brightness, without touching your gamma option. The lightmap
 * shader's inputs are written in order (ambient, sky factor, block flicker, night vision, darkness, darken, brightness).
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
	private static boolean polar$on() {
		return Polar.modules() != null && Polar.modules().fullbright.isEnabled();
	}

	@ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE",
		target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 3))
	private float polar$nightVision(float value) {
		return polar$on() ? 1.0f : value;
	}

	@ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE",
		target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 6))
	private float polar$brightness(float value) {
		return polar$on() ? 1.0f : value;
	}
}
