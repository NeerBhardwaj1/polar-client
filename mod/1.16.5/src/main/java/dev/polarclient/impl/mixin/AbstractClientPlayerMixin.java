package dev.polarclient.impl.mixin;

import dev.polarclient.cosmetics.Cosmetics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Polar capes in place of the game's cape (the elytra uses it too). */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getCloakTextureLocation", at = @At("RETURN"), cancellable = true)
	private void polar$cape(CallbackInfoReturnable<ResourceLocation> cir) {
		Object tex = Cosmetics.capeTexture(((AbstractClientPlayer) (Object) this).getUUID());
		if (tex instanceof ResourceLocation) cir.setReturnValue((ResourceLocation) tex);
	}
}
