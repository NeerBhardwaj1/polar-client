package dev.polarclient.impl;

import com.mojang.blaze3d.platform.NativeImage;
import dev.polarclient.Log;
import dev.polarclient.impl.mixin.OverlayTextureAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.server.packs.resources.Resource;

import java.util.IdentityHashMap;
import java.util.Map;

/** Hit Colour and Glint Colour: rewrites the game's hurt-overlay and enchantment-glint textures. */
final class Tints {
	/** The game's own hurt pixel: red, with 70% of the original colour kept (ARGB). */
	private static final int VANILLA_HURT = 0xB2FF0000;
	private static final Map<AbstractTexture, Integer> TINTED = new IdentityHashMap<>();

	private Tints() {
	}

	/** NativeImage in 1.16 stores ABGR. */
	private static int abgr(int argb) {
		return (argb & 0xFF00FF00) | (argb >> 16 & 0xFF) | (argb & 0xFF) << 16;
	}

	static void hurt(int argb) {
		DynamicTexture tex = ((OverlayTextureAccessor) Minecraft.getInstance().gameRenderer.overlayTexture()).polar$texture();
		NativeImage pixels = tex.getPixels();
		if (pixels == null) return;
		int pixel = argb == 0 ? VANILLA_HURT : ((255 - (argb >>> 24)) << 24) | (argb & 0xFFFFFF);
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 16; x++) pixels.setPixelRGBA(x, y, abgr(pixel));
		}
		tex.upload();
	}

	static void glint(int rgb) {
		Minecraft mc = Minecraft.getInstance();
		AbstractTexture texture = mc.getTextureManager().getTexture(ItemRenderer.ENCHANT_GLINT_LOCATION);
		if (texture == null) return;
		Integer done = TINTED.get(texture);
		if (done != null ? done == rgb : rgb < 0) return;
		try (Resource res = mc.getResourceManager().getResource(ItemRenderer.ENCHANT_GLINT_LOCATION); NativeImage image = NativeImage.read(res.getInputStream())) {
			if (rgb >= 0) {
				int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
				for (int y = 0; y < image.getHeight(); y++) {
					for (int x = 0; x < image.getWidth(); x++) {
						int c = image.getPixelRGBA(x, y); // ABGR
						int l = Math.max(c & 0xFF, Math.max(c >> 8 & 0xFF, c >> 16 & 0xFF));
						image.setPixelRGBA(x, y, (c & 0xFF000000) | (l * b / 255) << 16 | (l * g / 255) << 8 | l * r / 255);
					}
				}
			}
			texture.bind();
			// blur on and repeat (not clamp), like the game loads the glint
			image.upload(0, 0, 0, 0, 0, image.getWidth(), image.getHeight(), true, false, false, false);
			TINTED.put(texture, rgb);
		} catch (Exception e) {
			Log.error("Couldn't tint the enchantment glint", e);
		}
	}
}
