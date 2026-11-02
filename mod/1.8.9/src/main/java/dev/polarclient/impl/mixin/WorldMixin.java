package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import net.minecraft.world.dimension.Dimension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time & Weather: the client's world shows the chosen time (overworld) and weather. */
@Mixin(World.class)
public abstract class WorldMixin {
	@Shadow @Final public Dimension dimension;

	@Inject(method = "getSkyAngle", at = @At("HEAD"), cancellable = true)
	private void polar$time(float tickDelta, CallbackInfoReturnable<Float> cir) {
		if (!((Object) this instanceof ClientWorld) || Polar.modules() == null || dimension.getType() != 0) return;
		long fixed = Polar.modules().timeWeather.dayTime();
		if (fixed >= 0) cir.setReturnValue(dimension.getSkyAngle(fixed, 0f));
	}

	@Inject(method = "getRainGradient", at = @At("RETURN"), cancellable = true)
	private void polar$rain(float delta, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ClientWorld && Polar.modules() != null) cir.setReturnValue(Polar.modules().timeWeather.rain(cir.getReturnValue()));
	}

	@Inject(method = "getThunderGradient", at = @At("RETURN"), cancellable = true)
	private void polar$thunder(float delta, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ClientWorld && Polar.modules() != null) cir.setReturnValue(Polar.modules().timeWeather.thunder(cir.getReturnValue()));
	}
}
