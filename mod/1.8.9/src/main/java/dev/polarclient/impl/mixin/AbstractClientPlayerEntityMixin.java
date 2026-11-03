package dev.polarclient.impl.mixin;

import dev.polarclient.cosmetics.Cosmetics;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Polar capes in place of the game's cape. (In these mappings getSkinId is the cape; getCapeId is the skin.) */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
	@Inject(method = "getSkinId", at = @At("RETURN"), cancellable = true)
	private void polar$cape(CallbackInfoReturnable<Identifier> cir) {
		Object tex = Cosmetics.capeTexture(((AbstractClientPlayerEntity) (Object) this).getUuid());
		if (tex instanceof Identifier) cir.setReturnValue((Identifier) tex);
	}
}
