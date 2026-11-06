package dev.polarclient.impl;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.polarclient.Polar;
import dev.polarclient.impl.mixin.ShaderEffectAccessor;
import dev.polarclient.modules.visual.MotionBlurModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderEffect;
import net.minecraft.util.Identifier;

/**
 * Motion blur on 1.8.9: Polar's shader effect (assets/polarclient/shaders/post/motion_blur.json, using the program
 * minecraft:polar_motion_blur) drawn right after the world, before the HUD. Its "previous" frame is made again, empty,
 * whenever the blur restarts (switched on again, back from the menus, a new window size).
 */
public final class MotionBlur {
	private static final Identifier CHAIN = new Identifier("polarclient", "shaders/post/motion_blur.json");
	/** After this long without a blurred frame, the next one starts fresh. */
	private static final long RESTART_NANOS = 250_000_000L;
	private static ShaderEffect chain;
	private static int width = -1;
	private static int height = -1;
	private static long lastFrame;

	private MotionBlur() {
	}

	/** Draws the blur over the world; called by {@code GameRendererMixin} after the world (and entity outlines). */
	public static void render(MinecraftClient mc, float partialTicks) {
		MotionBlurModule m = Polar.modules() == null ? null : Polar.modules().motionBlur;
		if (m == null || !m.active()) {
			close();
			return;
		}
		if (!GLX.shadersSupported) {
			m.fail("this graphics card has no shader support", null);
			return;
		}
		try {
			if (chain == null) {
				chain = new ShaderEffect(mc.getTextureManager(), mc.getResourceManager(), mc.getFramebuffer(), CHAIN);
				width = -1;
			}
			int w = mc.width;
			int h = mc.height;
			long now = System.nanoTime();
			if (w != width || h != height || now - lastFrame > RESTART_NANOS) {
				chain.setupDimensions(w, h); // makes its frames again, empty
				width = w;
				height = h;
			}
			lastFrame = now;
			((ShaderEffectAccessor) chain).polar$passes().get(0).getProgram().getUniformByNameOrDummy("BlendFactor").set(m.blend());
			// the same state the game sets for its own shader effects
			GlStateManager.disableBlend();
			GlStateManager.disableDepthTest();
			GlStateManager.disableAlphaTest();
			GlStateManager.enableTexture();
			GlStateManager.matrixMode(5890);
			GlStateManager.pushMatrix();
			GlStateManager.loadIdentity();
			try {
				chain.render(partialTicks);
			} finally {
				GlStateManager.popMatrix();
			}
		} catch (Exception e) {
			close();
			m.fail("the motion blur shader failed", e);
		}
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
