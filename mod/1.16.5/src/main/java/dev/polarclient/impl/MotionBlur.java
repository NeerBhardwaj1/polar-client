package dev.polarclient.impl;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.polarclient.Polar;
import dev.polarclient.impl.mixin.PostChainAccessor;
import dev.polarclient.modules.visual.MotionBlurModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;

/**
 * Motion blur on 1.16.5: Polar's post chain (assets/polarclient/shaders/post/motion_blur.json, using the program
 * minecraft:polar_motion_blur) drawn right after the world, before the HUD. Its "previous" frame is made again, empty,
 * whenever the blur restarts (switched on again, back from the menus, a new window size).
 */
public final class MotionBlur {
	private static final ResourceLocation CHAIN = new ResourceLocation("polarclient", "shaders/post/motion_blur.json");
	/** After this long without a blurred frame, the next one starts fresh. */
	private static final long RESTART_NANOS = 250_000_000L;
	private static PostChain chain;
	private static int width = -1;
	private static int height = -1;
	private static long lastFrame;

	private MotionBlur() {
	}

	/** Draws the blur over the world; called by {@code GameRendererMixin} after the world (and entity outlines). */
	public static void render(Minecraft mc, float partialTicks) {
		MotionBlurModule m = Polar.modules() == null ? null : Polar.modules().motionBlur;
		if (m == null || !m.active()) {
			close();
			return;
		}
		try {
			if (chain == null) {
				chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), CHAIN);
				width = -1;
			}
			int w = mc.getWindow().getWidth();
			int h = mc.getWindow().getHeight();
			long now = System.nanoTime();
			if (w != width || h != height || now - lastFrame > RESTART_NANOS) {
				chain.resize(w, h); // makes its frames again, empty
				width = w;
				height = h;
			}
			lastFrame = now;
			((PostChainAccessor) chain).polar$passes().get(0).getEffect().safeGetUniform("BlendFactor").set(m.blend());
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
			chain.close();
		} catch (RuntimeException ignored) {
			// already gone with the old graphics state
		}
		chain = null;
	}
}
