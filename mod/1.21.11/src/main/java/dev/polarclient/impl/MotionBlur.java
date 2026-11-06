package dev.polarclient.impl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.polarclient.Polar;
import dev.polarclient.impl.mixin.PostChainAccessor;
import dev.polarclient.modules.visual.MotionBlurModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Motion blur on 1.21.11: Polar's post effect (assets/polarclient/post_effect/motion_blur_*.json) drawn right after the
 * world, before the HUD. The effect keeps the last blurred frame in its "previous" target; that's wiped whenever the
 * blur restarts (switched on again, back from a menu without a world, another strength), so no old frame shows through.
 */
public final class MotionBlur {
	/**
	 * True while Polar loads its effect. If loading fails, Minecraft would normally treat it as a broken resource pack
	 * (turn packs off, or even leave the world); {@code ShaderManagerMixin} skips that for Polar's effect.
	 */
	public static volatile boolean loading;
	/** After this long without a blurred frame, the next one starts fresh. */
	private static final long RESTART_NANOS = 250_000_000L;
	/** The effect drawn last (a closed one after a resource reload, which is fine: it keeps nothing then). */
	private static PostChain lastChain;
	private static long lastFrame;

	private MotionBlur() {
	}

	/** Draws the blur over the world; called by {@code GameRendererMixin} after the world (and entity outlines). */
	public static void render(Minecraft mc, GraphicsResourceAllocator resources) {
		MotionBlurModule m = Polar.modules() == null ? null : Polar.modules().motionBlur;
		if (m == null || !m.active() || mc.level == null) {
			if (lastChain != null) {
				forget(lastChain); // switched off: free the kept frame
				lastChain = null;
			}
			return;
		}
		Identifier id = Identifier.fromNamespaceAndPath("polarclient", m.effectName());
		PostChain chain = load(mc, m, id);
		if (chain == null) return;
		long now = System.nanoTime();
		if (chain != lastChain || now - lastFrame > RESTART_NANOS) {
			if (lastChain != null && lastChain != chain) forget(lastChain);
			forget(chain);
		}
		lastChain = chain;
		lastFrame = now;
		try {
			chain.process(mc.getMainRenderTarget(), resources);
		} catch (RuntimeException e) {
			m.fail("drawing " + id + " failed", e);
			forget(chain);
			lastChain = null;
		}
	}

	/** Drops the effect's kept frame (it's made again, empty, on the next frame drawn). */
	private static void forget(PostChain chain) {
		if (chain == null) return;
		Map<Identifier, RenderTarget> kept = ((PostChainAccessor) chain).polar$persistentTargets();
		for (RenderTarget target : kept.values()) target.destroyBuffers();
		kept.clear();
	}

	/** Loads (or finds the already loaded) effect; on failure the module is switched off instead. */
	private static PostChain load(Minecraft mc, MotionBlurModule m, Identifier id) {
		PostChain chain;
		loading = true;
		try {
			chain = mc.getShaderManager().getPostChain(id, LevelTargetBundle.MAIN_TARGETS);
		} catch (RuntimeException e) {
			m.fail("loading " + id + " failed", e);
			return null;
		} finally {
			loading = false;
		}
		if (chain == null) m.fail("loading " + id + " failed (see the error above)", null);
		return chain;
	}
}
