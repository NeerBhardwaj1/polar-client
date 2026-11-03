package dev.polarclient.impl.mixin;

import dev.polarclient.impl.PolarCosmeticsFeature;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Polar cosmetics on players: the model layer. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
	@Inject(method = "<init>(Lnet/minecraft/client/render/entity/EntityRenderDispatcher;Z)V", at = @At("TAIL"))
	private void polar$addFeature(EntityRenderDispatcher dispatcher, boolean slim, CallbackInfo ci) {
		((LivingEntityRendererInvoker) this).polar$addFeature(new PolarCosmeticsFeature((PlayerEntityRenderer) (Object) this));
	}
}
