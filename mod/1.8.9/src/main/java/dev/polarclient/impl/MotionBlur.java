package dev.polarclient.impl;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.polarclient.impl.mixin.ShaderEffectAccessor;
import dev.polarclient.modules.visual.MotionBlurModule;
import dev.polarclient.render.CameraBlur;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.JsonGlProgram;
import net.minecraft.client.gl.ShaderEffect;
import net.minecraft.util.Identifier;

/**
 * Motion blur on 1.8.9: Polar's shader effect (assets/polarclient/shaders/post/motion_blur.json, using the program
 * minecraft:polar_motion_blur) drawn right after the world and before your hand, so the hand, the HUD and menus stay
 * sharp. The camera numbers from {@link CameraBlur} are set on the program's uniforms each frame.
 */
public final class MotionBlur {
	private static final Identifier CHAIN = new Identifier("polarclient", "shaders/post/motion_blur.json");
	private static ShaderEffect chain;
	private static int width = -1;
	private static int height = -1;

	private MotionBlur() {
	}

	/**
	 * Blurs the world for this frame while the camera turns; called by {@code GameRendererMixin} before the hand.
	 * yaw and pitch: the camera's, in degrees.
	 */
	public static void render(MinecraftClient mc, float yaw, float pitch, float fovDegrees, float partialTicks) {
		MotionBlurModule m = CameraBlur.active();
		if (m == null) {
			close();
			return;
		}
		if (!GLX.shadersSupported) {
			m.fail("this graphics card has no shader support", null);
			return;
		}
		if (mc.options.anaglyph3d) return; // the 3D glasses mode draws the world twice, one colour each
		int w = mc.width, h = mc.height;
		if (w <= 0 || h <= 0) return;
		float tanY = (float) Math.tan(Math.toRadians(fovDegrees) / 2);
		if (!CameraBlur.update(m, yaw, pitch, tanY * w / h, tanY, h)) return;
		try {
			if (chain == null) {
				chain = new ShaderEffect(mc.getTextureManager(), mc.getResourceManager(), mc.getFramebuffer(), CHAIN);
				width = -1;
			}
			if (w != width || h != height) {
				chain.setupDimensions(w, h);
				width = w;
				height = h;
			}
			float[] p = CameraBlur.params();
			JsonGlProgram program = ((ShaderEffectAccessor) chain).polar$passes().get(0).getProgram();
			set(program.getUniformByNameOrDummy("TurnX"), p, 0);
			set(program.getUniformByNameOrDummy("TurnY"), p, 3);
			set(program.getUniformByNameOrDummy("TurnZ"), p, 6);
			program.getUniformByNameOrDummy("Lens").set(p[9], p[10], p[11], 0f);
			// the same state the game sets for its own shader effects
			GlStateManager.disableBlend();
			GlStateManager.disableAlphaTest();
			GlStateManager.enableTexture();
			GlStateManager.matrixMode(5890);
			GlStateManager.pushMatrix();
			GlStateManager.loadIdentity();
			try {
				chain.render(partialTicks);
			} finally {
				GlStateManager.popMatrix();
				GlStateManager.matrixMode(5888);
				// back to drawing into the game's picture, for the hand
				mc.getFramebuffer().bind(true);
				GlStateManager.enableAlphaTest();
				GlStateManager.enableDepthTest();
				GlStateManager.depthMask(true);
			}
		} catch (Exception e) {
			close();
			m.fail("the motion blur shader failed", e);
		}
	}

	private static void set(GlUniform u, float[] p, int i) {
		u.set(p[i], p[i + 1], p[i + 2]);
	}

	/** Frees the effect (switched off). */
	private static void close() {
		if (chain == null) return;
		try {
			chain.disable();
		} catch (RuntimeException ignored) {
			// already gone with the old graphics state
		}
		chain = null;
	}
}
