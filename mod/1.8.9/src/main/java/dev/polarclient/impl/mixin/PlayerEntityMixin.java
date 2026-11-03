package dev.polarclient.impl.mixin;

import dev.polarclient.cosmetics.Cosmetics;
import dev.polarclient.impl.CosmeticsRender;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Polar badge in front of the names of players on Polar (their name tags). Client-side players only. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
	@Inject(method = "getName", at = @At("RETURN"), cancellable = true)
	private void polar$badge(CallbackInfoReturnable<Text> cir) {
		Object self = this;
		if (!(self instanceof AbstractClientPlayerEntity) || cir.getReturnValue() == null) return;
		if (!Cosmetics.badge(((AbstractClientPlayerEntity) self).getUuid())) return;
		Text name = new LiteralText(CosmeticsRender.BADGE);
		name.append(cir.getReturnValue());
		cir.setReturnValue(name);
	}
}
