package dev.polarclient.impl.mixin;

import dev.polarclient.impl.MotionBlur;
import net.minecraft.client.renderer.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When a post effect fails to load, the game assumes a resource pack broke it and turns packs off (or leaves the
 * world). If Polar's motion blur fails on some graphics driver, only the module switches off instead.
 */
@Mixin(ShaderManager.class)
public abstract class ShaderManagerMixin {
	@Inject(method = "tryTriggerRecovery", at = @At("HEAD"), cancellable = true)
	private void polar$keepPacks(Exception exception, CallbackInfo ci) {
		if (MotionBlur.loading) ci.cancel();
	}
}
