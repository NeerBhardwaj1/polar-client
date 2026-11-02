package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clear Water: under water the fog starts far away, like on land. */
@Mixin(WaterFogEnvironment.class)
public abstract class WaterFogEnvironmentMixin {
	@Inject(method = "setupFog", at = @At("TAIL"))
	private void polar$clearWater(FogData fog, Entity entity, BlockPos pos, ClientLevel level, float renderDistance, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (Polar.modules() == null || !Polar.modules().clearWater.isEnabled()) return;
		fog.environmentalStart = renderDistance * 0.4f;
		fog.environmentalEnd = renderDistance;
		fog.skyEnd = renderDistance;
		fog.cloudEnd = renderDistance;
	}
}
