package dev.polarclient.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import dev.polarclient.cosmetics.RenderBatch;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;

/** Draws Polar cosmetics (quads made by {@link dev.polarclient.cosmetics.Cosmetics}) and the Polar badge. */
public final class CosmeticsRender {
	private static final ResourceLocation BADGE_FONT = new ResourceLocation("polarclient", "badge");
	private static final int FULL_BRIGHT = 0xF000F0;

	private CosmeticsRender() {
	}

	private static RenderType type(RenderBatch b) {
		ResourceLocation t = (ResourceLocation) b.texture;
		switch (b.layer) {
			case RenderBatch.CUTOUT:
				return RenderType.entityCutout(t);
			case RenderBatch.TRANSLUCENT:
				return RenderType.entityTranslucentCull(t);
			case RenderBatch.GLOW:
				return RenderType.eyes(t);
			default:
				return RenderType.entityTranslucent(t);
		}
	}

	public static void draw(PoseStack poseStack, MultiBufferSource buffers, RenderBatch b, int light) {
		if (!(b.texture instanceof ResourceLocation) || b.vertices() == 0) return;
		int l = b.layer == RenderBatch.GLOW ? FULL_BRIGHT : light;
		VertexConsumer vc = buffers.getBuffer(type(b));
		Matrix4f pose = poseStack.last().pose();
		Matrix3f normal = poseStack.last().normal();
		for (int i = 0; i < b.vertices(); i++) {
			int c = b.color(i);
			vc.vertex(pose, b.x(i), b.y(i), b.z(i)).color(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24).uv(b.u(i), b.v(i))
				.overlayCoords(OverlayTexture.NO_OVERLAY).uv2(l).normal(normal, b.nx(i), b.ny(i), b.nz(i)).endVertex();
		}
	}

	/** The name with the Polar badge (a snowflake from the polarclient:badge font) in front. */
	public static Component badged(Component name) {
		MutableComponent badge = new TextComponent("").withStyle(s -> s.withFont(BADGE_FONT).withColor(ChatFormatting.WHITE).withBold(false).withItalic(false));
		return new TextComponent("").append(badge).append(" ").append(name);
	}
}
