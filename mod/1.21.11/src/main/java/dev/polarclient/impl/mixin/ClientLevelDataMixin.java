package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time & Weather: the client's copy of the time of day reads the chosen time in the overworld. */
@Mixin(ClientLevel.ClientLevelData.class)
public abstract class ClientLevelDataMixin {
	@Inject(method = "getDayTime", at = @At("RETURN"), cancellable = true)
	private void polar$time(CallbackInfoReturnable<Long> cir) {
		if (Polar.modules() == null) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) return;
		long fixed = Polar.modules().timeWeather.dayTime();
		if (fixed >= 0) cir.setReturnValue(Math.floorDiv(cir.getReturnValue(), 24000L) * 24000L + fixed);
	}
}
