package dev.polarclient.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.polarclient.modules.visual.MotionBlurModule;
import dev.polarclient.render.CameraBlur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Motion blur on 26.3: Polar's post effect (assets/polarclient/post_effect/motion_blur.json) drawn right after the
 * world and before your hand, so the hand, the HUD and menus stay sharp. The camera numbers from {@link CameraBlur}
 * reach the shader through a small texture (one float per texel), since a post effect's own settings are fixed.
 */
public final class MotionBlur {
	/**
	 * True while Polar loads its effect. If loading fails, Minecraft would normally treat it as a broken resource pack
	 * (turn packs off, or even leave the world); {@code ShaderManagerMixin} skips that for Polar's effect.
	 */
	public static volatile boolean loading;
	private static final Identifier CHAIN = Identifier.fromNamespaceAndPath("polarclient", "motion_blur");
	/** The effect reads "polarclient:motion_blur_params", which the game looks up under textures/effect/. */
	private static final Identifier PARAMS = Identifier.fromNamespaceAndPath("polarclient", "textures/effect/motion_blur_params.png");
	private static DynamicTexture params;

	private MotionBlur() {
	}

	/** Blurs the world for this frame while the camera turns; called by {@code GameRendererMixin} before the hand. */
	public static void render(Minecraft mc, RenderTarget main, GraphicsResourceAllocator resources, CameraRenderState camera, int height) {
		MotionBlurModule m = CameraBlur.active();
		if (m == null || mc.level == null || height <= 0) return;
		// the projection's scale on x and y is 1 / tan(half the field of view); kept signed, so a projection that flips
		// the picture (some graphics backends do) flips the blur's direction the same way
		float sx = camera.projectionMatrix.m00(), sy = camera.projectionMatrix.m11();
		if (Math.abs(sx) < 1e-4f || Math.abs(sy) < 1e-4f) return;
		if (!CameraBlur.update(m, camera.yRot, camera.xRot, 1f / sx, 1f / sy, height)) return;
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
			chain.process(main, resources);
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
