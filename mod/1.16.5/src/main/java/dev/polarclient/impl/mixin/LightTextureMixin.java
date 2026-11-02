package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import net.minecraft.client.Options;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Fullbright: the lightmap is built as if brightness were far past "Bright", without touching your option. */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
	@Redirect(method = "updateLightTexture", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;gamma:D", opcode = 180))
	private double polar$gamma(Options options) {
		return Polar.modules() != null && Polar.modules().fullbright.isEnabled() ? 16.0 : options.gamma;
	}
}
