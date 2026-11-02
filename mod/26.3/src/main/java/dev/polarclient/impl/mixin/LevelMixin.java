package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time & Weather: the client's world reports the chosen weather (never the server's world). */
@Mixin(Level.class)
public abstract class LevelMixin {
	@Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
	private void polar$rain(float a, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ClientLevel && Polar.modules() != null) cir.setReturnValue(Polar.modules().timeWeather.rain(cir.getReturnValue()));
	}

	@Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
	private void polar$thunder(float a, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ClientLevel && Polar.modules() != null) cir.setReturnValue(Polar.modules().timeWeather.thunder(cir.getReturnValue()));
	}
}
