package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hide Far Entities: entities past the set distance aren't drawn (nor their name tags and shadows). */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void polar$hideFar(Entity entity, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
		if (Polar.modules() == null || !Polar.modules().hideFarEntities.isEnabled()) return;
		if (entity == Minecraft.getInstance().getCameraEntity()) return;
		if (Polar.modules().hideFarEntities.hides(entity instanceof Player, entity.distanceToSqr(camX, camY, camZ))) cir.setReturnValue(false);
	}
}
