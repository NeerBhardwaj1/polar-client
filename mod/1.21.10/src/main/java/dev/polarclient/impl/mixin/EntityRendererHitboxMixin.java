package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Hitboxes mod's "players only": other entities get no hitbox state, so none is drawn. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererHitboxMixin {
	@Inject(method = "extractHitboxes(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("HEAD"), cancellable = true)
	private void polar$playersOnly(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
		if (Polar.modules() != null && Polar.modules().hitboxes.skips(entity instanceof Player)) {
			state.hitboxesRenderState = null;
			state.serverHitboxesRenderState = null;
			ci.cancel();
		}
	}
}
