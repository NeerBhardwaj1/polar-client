package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import dev.polarclient.modules.perf.XpBottleOptimizerModule;
import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** XP Bottle Optimizer: tells it while an XP bottle's splash (level event 2002 in a water bottle's colour) makes particles. */
@Mixin(LevelEventHandler.class)
public abstract class LevelEventHandlerMixin {
	@Inject(method = "levelEvent", at = @At("HEAD"))
	private void polar$splashStart(int type, BlockPos pos, int data, CallbackInfo ci) {
		if (Polar.modules() != null) Polar.modules().xpBottles.splashStart(type == 2002 && (data & 0xFFFFFF) == XpBottleOptimizerModule.XP_SPLASH_COLOR);
	}

	@Inject(method = "levelEvent", at = @At("RETURN"))
	private void polar$splashEnd(int type, BlockPos pos, int data, CallbackInfo ci) {
		if (Polar.modules() != null) Polar.modules().xpBottles.splashEnd();
	}
}
