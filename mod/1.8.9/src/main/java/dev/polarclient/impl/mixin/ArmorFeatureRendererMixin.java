package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.polarclient.impl.Tints;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Glint Colour on armour (the game tints its armour glint at 76% brightness). */
@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin {
	@Redirect(method = "renderGlint", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;color(FFFF)V", ordinal = 1))
	private void polar$glint(float r, float g, float b, float a) {
		int rgb = Tints.glint;
		if (rgb < 0) GlStateManager.color(r, g, b, a);
		else GlStateManager.color((rgb >> 16 & 0xFF) / 255f * 0.76f, (rgb >> 8 & 0xFF) / 255f * 0.76f, (rgb & 0xFF) / 255f * 0.76f, a);
	}
}
