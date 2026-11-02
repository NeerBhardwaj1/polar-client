package dev.polarclient.impl;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.polarclient.Log;
import dev.polarclient.impl.mixin.OverlayTextureAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.util.IdentityHashMap;
import java.util.Map;

/** Hit Colour and Glint Colour: rewrites the game's hurt-overlay and enchantment-glint textures. */
final class Tints {
	/** The game's own hurt pixel: red, with 70% of the original colour kept. */
	private static final int VANILLA_HURT = 0xB2FF0000;
	private static final Map<AbstractTexture, Integer> TINTED = new IdentityHashMap<>();

	private Tints() {
	}

	static void hurt(int argb) {
		DynamicTexture tex = ((OverlayTextureAccessor) Minecraft.getInstance().gameRenderer.overlayTexture()).polar$texture();
		NativeImage pixels = tex.getPixels();
		if (pixels == null) return;
		// the overlay's alpha is how much of the original colour stays; the setting's alpha is the tint's strength
		int pixel = argb == 0 ? VANILLA_HURT : ((255 - (argb >>> 24)) << 24) | (argb & 0xFFFFFF);
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 16; x++) pixels.setPixel(x, y, pixel);
		}
		tex.upload();
	}

	static void glint(int rgb) {
		tint(ItemRenderer.ENCHANTED_GLINT_ITEM, rgb);
		tint(ItemRenderer.ENCHANTED_GLINT_ARMOR, rgb);
	}

	private static void tint(ResourceLocation id, int rgb) {
		Minecraft mc = Minecraft.getInstance();
		AbstractTexture texture = mc.getTextureManager().getTexture(id);
		Integer done = TINTED.get(texture);
		if (done != null ? done == rgb : rgb < 0) return; // already like that (a reloaded texture starts out as the game's)
		try (InputStream in = mc.getResourceManager().open(id); NativeImage image = NativeImage.read(in)) {
			if (rgb >= 0) {
				int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
				for (int y = 0; y < image.getHeight(); y++) {
					for (int x = 0; x < image.getWidth(); x++) {
						int c = image.getPixel(x, y);
						int l = Math.max(c >> 16 & 0xFF, Math.max(c >> 8 & 0xFF, c & 0xFF));
						image.setPixel(x, y, (c & 0xFF000000) | (l * r / 255) << 16 | (l * g / 255) << 8 | l * b / 255);
					}
				}
			}
			RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.getTexture(), image);
			TINTED.put(texture, rgb);
		} catch (Exception e) {
			Log.error("Couldn't tint " + id, e);
		}
	}
}
