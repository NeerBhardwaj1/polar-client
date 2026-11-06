package dev.polarclient.impl;

import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.polarclient.impl.mixin.PostChainAccessor;
import dev.polarclient.modules.visual.MotionBlurModule;
import dev.polarclient.render.CameraBlur;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;

/**
 * Motion blur on 1.16.5: Polar's post chain (assets/polarclient/shaders/post/motion_blur.json, using the program
 * minecraft:polar_motion_blur) drawn right after the world and before your hand, so the hand, the HUD and menus stay
 * sharp. The camera numbers from {@link CameraBlur} are set on the program's uniforms each frame.
 */
public final class MotionBlur {
	private static final ResourceLocation CHAIN = new ResourceLocation("polarclient", "shaders/post/motion_blur.json");
	private static PostChain chain;
	private static int width = -1;
	private static int height = -1;

	private MotionBlur() {
	}

	/** Blurs the world for this frame while the camera turns; called by {@code GameRendererMixin} before the hand. */
	public static void render(Minecraft mc, Camera camera, double fovDegrees, float partialTicks) {
		MotionBlurModule m = CameraBlur.active();
		if (m == null) {
			close();
			return;
		}
		int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
		if (w <= 0 || h <= 0) return;
		float tanY = (float) Math.tan(Math.toRadians(fovDegrees) / 2);
		if (!CameraBlur.update(m, camera.getYRot(), camera.getXRot(), tanY * w / h, tanY, h)) return;
		try {
			if (chain == null) {
				chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), CHAIN);
				width = -1;
			}
			if (w != width || h != height) {
				chain.resize(w, h);
				width = w;
				height = h;
			}
			float[] p = CameraBlur.params();
			EffectInstance effect = ((PostChainAccessor) chain).polar$passes().get(0).getEffect();
			set(effect.safeGetUniform("TurnX"), p, 0);
			set(effect.safeGetUniform("TurnY"), p, 3);
			set(effect.safeGetUniform("TurnZ"), p, 6);
			effect.safeGetUniform("Lens").set(p[9], p[10], p[11], 0f);
			// the same state the game sets for its own post effects
			RenderSystem.disableBlend();
			RenderSystem.disableDepthTest();
			RenderSystem.disableAlphaTest();
			RenderSystem.enableTexture();
			RenderSystem.matrixMode(5890);
			RenderSystem.pushMatrix();
			RenderSystem.loadIdentity();
			try {
				chain.process(partialTicks);
			} finally {
				RenderSystem.popMatrix();
				RenderSystem.matrixMode(5888);
				// back to drawing into the game's picture, for the hand
				mc.getMainRenderTarget().bindWrite(true);
				RenderSystem.enableDepthTest();
			}
		} catch (Exception e) {
			close();
			m.fail("the motion blur shader failed", e);
		}
	}

	private static void set(AbstractUniform u, float[] p, int i) {
		u.set(p[i], p[i + 1], p[i + 2]);
	}

	/** Frees the effect (switched off). */
	private static void close() {
		if (chain == null) return;
		try {
			chain.close();
		} catch (RuntimeException ignored) {
			// already gone with the old graphics state
		}
		chain = null;
	}
}
