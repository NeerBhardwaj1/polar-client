package dev.polarclient.impl.mixin;

import dev.polarclient.impl.HealthTags;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Health Indicator: with "Show on mobs" on, mobs near you get a name tag, so their health can show in it. */
@Mixin(MobRenderer.class)
public abstract class MobRendererMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Mob;)Z", at = @At("RETURN"), cancellable = true)
	private void polar$healthTag(Mob mob, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && HealthTags.forceMobTag(mob)) cir.setReturnValue(true);
	}
}
