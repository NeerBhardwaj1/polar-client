package dev.polarclient.impl;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.polarclient.modules.visual.MotionBlurModule;
import dev.polarclient.render.CameraBlur;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Motion blur on 1.21.10: Polar's post effect (assets/polarclient/post_effect/motion_blur.json) drawn right after the
 * world and before your hand, so the hand, the HUD and menus stay sharp. The camera numbers from {@link CameraBlur}
 * reach the shader through a small texture (one float per texel), since a post effect's own settings are fixed.
 */
public final class MotionBlur {
	/**
	 * True while Polar loads its effect. If loading fails, Minecraft would normally treat it as a broken resource pack
	 * (turn packs off, or even leave the world); {@code ShaderManagerMixin} skips that for Polar's effect.
	 */
	public static volatile boolean loading;
	private static final ResourceLocation CHAIN = ResourceLocation.fromNamespaceAndPath("polarclient", "motion_blur");
	/** The effect reads "polarclient:motion_blur_params", which the game looks up under textures/effect/. */
	private static final ResourceLocation PARAMS = ResourceLocation.fromNamespaceAndPath("polarclient", "textures/effect/motion_blur_params.png");
	private static DynamicTexture params;

	private MotionBlur() {
	}

	/** Blurs the world for this frame while the camera turns; called by {@code GameRendererMixin} before the hand. */
	public static void render(Minecraft mc, GraphicsResourceAllocator resources, Camera camera, float fovDegrees) {
		MotionBlurModule m = CameraBlur.active();
		if (m == null || mc.level == null) return;
		int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
		if (w <= 0 || h <= 0) return;
		float tanY = (float) Math.tan(Math.toRadians(fovDegrees) / 2);
		if (!CameraBlur.update(m, camera.getYRot(), camera.getXRot(), tanY * w / h, tanY, h)) return;
		try {
			if (params == null) {
				params = new DynamicTexture(() -> "Polar motion blur", CameraBlur.PARAMS, 1, true);
				mc.getTextureManager().register(PARAMS, params);
			}
			float[] p = CameraBlur.params();
			for (int i = 0; i < CameraBlur.PARAMS; i++) params.getPixels().setPixelABGR(i, 0, Float.floatToRawIntBits(p[i]));
			params.upload();
		} catch (RuntimeException e) {
			m.fail("couldn't make its settings texture", e);
			return;
		}
		PostChain chain = load(mc, m);
		if (chain == null) return;
		try {
			chain.process(mc.getMainRenderTarget(), resources);
		} catch (RuntimeException e) {
			m.fail("drawing " + CHAIN + " failed", e);
		}
	}

	/** Loads (or finds the already loaded) effect; on failure the module is switched off instead. */
	private static PostChain load(Minecraft mc, MotionBlurModule m) {
		PostChain chain;
		loading = true;
		try {
			chain = mc.getShaderManager().getPostChain(CHAIN, LevelTargetBundle.MAIN_TARGETS);
		} catch (RuntimeException e) {
			m.fail("loading " + CHAIN + " failed", e);
			return null;
		} finally {
			loading = false;
		}
		if (chain == null) m.fail("loading " + CHAIN + " failed (see the error above)", null);
		return chain;
	}
}
