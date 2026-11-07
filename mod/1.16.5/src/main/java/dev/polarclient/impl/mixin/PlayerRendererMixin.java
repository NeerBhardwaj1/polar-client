package dev.polarclient.impl.mixin;

import dev.polarclient.cosmetics.Cosmetics;
import dev.polarclient.impl.CosmeticsRender;
import dev.polarclient.impl.HealthTags;
import dev.polarclient.impl.PolarCosmeticsLayer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Polar cosmetics on players: the model layer, the Polar badge in front of name tags and Health Indicator after them. */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
	@Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;Z)V", at = @At("TAIL"))
	private void polar$addLayer(EntityRenderDispatcher dispatcher, boolean slim, CallbackInfo ci) {
		((LivingEntityRendererInvoker) this).polar$addLayer(new PolarCosmeticsLayer((PlayerRenderer) (Object) this));
	}

	@ModifyVariable(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), argsOnly = true)
	private Component polar$badge(Component name, AbstractClientPlayer player) {
		if (name == null) return null;
		return HealthTags.withHealth(Cosmetics.badge(player.getUUID()) ? CosmeticsRender.badged(name) : name, player);
	}
}
