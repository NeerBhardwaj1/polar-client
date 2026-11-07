package dev.polarclient.impl.mixin;

import dev.polarclient.impl.HealthTags;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Health Indicator: with "Show on mobs" on, mobs near you get a name tag, so their health can show in it. */
@Mixin(MobEntityRenderer.class)
public abstract class MobEntityRendererMixin {
	@Inject(method = "hasLabel(Lnet/minecraft/entity/mob/MobEntity;)Z", at = @At("RETURN"), cancellable = true)
	private void polar$healthTag(MobEntity mob, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && HealthTags.forceMobTag(mob)) cir.setReturnValue(true);
	}
}
