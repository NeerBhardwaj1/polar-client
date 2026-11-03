package dev.polarclient.impl.mixin;

import dev.polarclient.cosmetics.Cosmetics;
import dev.polarclient.impl.CosmeticsRender;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Polar badge in front of the name tags of players on Polar. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
	private void polar$badge(Entity entity, CallbackInfoReturnable<Component> cir) {
		Component name = cir.getReturnValue();
		if (name != null && entity instanceof Player && Cosmetics.badge(entity.getUUID())) cir.setReturnValue(CosmeticsRender.badged(name));
	}
}
