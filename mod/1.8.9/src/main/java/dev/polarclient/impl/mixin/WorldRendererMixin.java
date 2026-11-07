package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** XP Bottle Optimizer: tells it while an XP bottle's splash (world event 2002 with data 0 on 1.8.9) makes particles. */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
	@Inject(method = "processWorldEvent", at = @At("HEAD"))
	private void polar$splashStart(PlayerEntity player, int type, BlockPos pos, int data, CallbackInfo ci) {
		if (Polar.modules() != null) Polar.modules().xpBottles.splashStart(type == 2002 && data == 0);
	}

	@Inject(method = "processWorldEvent", at = @At("RETURN"))
	private void polar$splashEnd(PlayerEntity player, int type, BlockPos pos, int data, CallbackInfo ci) {
		if (Polar.modules() != null) Polar.modules().xpBottles.splashEnd();
	}
}
