package dev.polarclient.impl.mixin;

import dev.polarclient.impl.Tints;
import net.minecraft.client.render.item.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Glint Colour on items. */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
	@ModifyArg(method = "renderGlint", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/render/item/ItemRenderer;renderBakedItemModel(Lnet/minecraft/client/render/model/BakedModel;I)V"), index = 1)
	private int polar$glint(int color) {
		int rgb = Tints.glint;
		return rgb < 0 ? color : 0xFF000000 | rgb;
	}
}
