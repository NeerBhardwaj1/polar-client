package dev.polarclient.mixin;

import dev.polarclient.PolarClient;
import dev.polarclient.modules.FreelookModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While Freelook is active, mouse movement turns the camera instead of your player. */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void polar$freelookTurn(double xo, double yo, CallbackInfo ci) {
		if (PolarClient.modules() == null) return;
		FreelookModule f = PolarClient.modules().freelook;
		if (f.isActive() && (Object) this == Minecraft.getInstance().player) {
			f.turn(xo, yo);
			ci.cancel();
		}
	}
}
