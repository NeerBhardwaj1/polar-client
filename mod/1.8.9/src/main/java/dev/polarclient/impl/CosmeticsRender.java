package dev.polarclient.impl;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.polarclient.cosmetics.RenderBatch;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

import java.util.List;

/** Draws Polar cosmetics (quads made by {@link dev.polarclient.cosmetics.Cosmetics}) with the 1.8.9 renderer. */
public final class CosmeticsRender {
	/** The Polar badge: a snowflake from the game's own unicode font, in Polar ice blue. */
	public static final String BADGE = "§b❄§r ";

	private CosmeticsRender() {
	}

	/**
	 * Draws the batches with the current matrix. light: the entity's lightmap coordinates (Entity#getLightmapCoordinates),
	 * restored afterwards; particles: true for aura sprites (no shading).
	 */
	public static void draw(List<RenderBatch> batches, int light, boolean particles) {
		if (batches.isEmpty()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		float lx = light % 65536, ly = light / 65536;
		if (particles) GlStateManager.disableLighting();
		for (RenderBatch b : batches) {
			if (!(b.texture instanceof Identifier) || b.vertices() == 0) continue;
			mc.getTextureManager().bindTexture((Identifier) b.texture);
			boolean glow = b.layer == RenderBatch.GLOW;
			boolean blend = b.layer != RenderBatch.CUTOUT;
			if (blend) {
				GlStateManager.enableBlend();
				if (glow) GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE);
				else GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
				GlStateManager.depthMask(false);
			}
			if (b.layer == RenderBatch.TRANSLUCENT_BOTH) GlStateManager.disableCull();
			else GlStateManager.enableCull();
			if (glow) {
				GlStateManager.disableLighting();
				GLX.gl13MultiTexCoord2f(GLX.lightmapTextureUnit, 240f, 240f);
			}
			GlStateManager.color(1f, 1f, 1f, 1f);
			Tessellator t = Tessellator.getInstance();
			BufferBuilder bb = t.getBuffer();
			bb.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
			for (int i = 0; i < b.vertices(); i++) {
				int c = b.color(i);
				bb.vertex(b.x(i), b.y(i), b.z(i)).texture(b.u(i), b.v(i)).color(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24).normal(b.nx(i), b.ny(i), b.nz(i)).next();
			}
			t.draw();
			if (glow) {
				GLX.gl13MultiTexCoord2f(GLX.lightmapTextureUnit, lx, ly);
				if (!particles) GlStateManager.enableLighting();
			}
			if (blend) {
				GlStateManager.depthMask(true);
				GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
				GlStateManager.disableBlend();
			}
			GlStateManager.enableCull();
		}
		if (particles) GlStateManager.enableLighting();
	}
}
