package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import dev.polarclient.modules.FreelookModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While Freelook is active, mouse movement turns the camera instead of your player. */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "increaseTransforms", at = @At("HEAD"), cancellable = true)
	private void polar$freelookTurn(float yaw, float pitch, CallbackInfo ci) {
		if (Polar.modules() == null) return;
		FreelookModule f = Polar.modules().freelook;
		if (f.isActive() && (Object) this == MinecraftClient.getInstance().player) {
			// 1.8.9 subtracts the pitch movement where newer versions add it
			f.turn(yaw, -pitch);
			ci.cancel();
		}
	}
}
