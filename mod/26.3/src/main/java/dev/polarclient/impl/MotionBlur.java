package dev.polarclient.impl;

import dev.polarclient.Polar;
import dev.polarclient.modules.visual.MotionBlurModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Motion blur on 26.3: Polar's post effect (assets/polarclient/post_effect/motion_blur_*.json) joins the game's own
 * list of post effects for the frame, so the game draws it after the world and before the HUD, and frees it when it's
 * switched off or the strength changes.
 */
public final class MotionBlur {
	/**
	 * True while Polar loads its effect. If loading fails, Minecraft would normally treat it as a broken resource pack
	 * (turn packs off, or even leave the world); {@code ShaderManagerMixin} skips that for Polar's effect.
	 */
	public static volatile boolean loading;

	private MotionBlur() {
	}

	/** Adds the motion blur effect to this frame's post effects when it's on (called after the game fills the list). */
	public static void request(Minecraft mc, List<Identifier> requested, List<Identifier> failed) {
		MotionBlurModule m = Polar.modules() == null ? null : Polar.modules().motionBlur;
		if (m == null || !m.active() || mc.level == null) return;
		Identifier id = Identifier.fromNamespaceAndPath("polarclient", m.effectName());
		if (failed.contains(id)) {
			m.fail("drawing " + id + " failed (see the warning above)", null);
			return;
		}
		if (load(mc, m, id) != null) requested.add(id);
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
