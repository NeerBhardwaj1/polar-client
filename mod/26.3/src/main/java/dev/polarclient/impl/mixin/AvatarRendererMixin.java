package dev.polarclient.impl.mixin;

import dev.polarclient.cosmetics.Cosmetics;
import dev.polarclient.impl.PolarCosmeticsLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Polar cosmetics on players: the model layer, and Polar capes in place of the game's cape. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void polar$addLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
		((LivingEntityRendererInvoker) this).polar$addLayer(new PolarCosmeticsLayer((AvatarRenderer<?>) (Object) this));
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
	private void polar$cape(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		Object tex = Cosmetics.capeTexture(entity.getUUID());
		if (!(tex instanceof Identifier id)) return;
		ClientAsset.Texture cape = new ClientAsset.ResourceTexture(id, id);
		PlayerSkin s = state.skin;
		state.skin = new PlayerSkin(s.body(), cape, cape, s.model(), s.secure());
	}
}
