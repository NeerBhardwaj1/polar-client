package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import net.minecraft.client.ClientClockManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time & Weather: the overworld clock reads the chosen time of day (the client's own copy only). */
@Mixin(ClientClockManager.ClientClockInstance.class)
public abstract class ClientClockInstanceMixin {
	private static long polar$fixed() {
		if (Polar.modules() == null) return -1;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) return -1;
		return Polar.modules().timeWeather.dayTime();
	}

	@Inject(method = "totalTicks", at = @At("RETURN"), cancellable = true)
	private void polar$time(CallbackInfoReturnable<Long> cir) {
		long fixed = polar$fixed();
		if (fixed >= 0) cir.setReturnValue(Math.floorDiv(cir.getReturnValue(), 24000L) * 24000L + fixed);
	}

	@Inject(method = "partialTick", at = @At("RETURN"), cancellable = true)
	private void polar$partial(CallbackInfoReturnable<Float> cir) {
		if (polar$fixed() >= 0) cir.setReturnValue(0f);
	}
}
