package dev.polarclient.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.polarclient.cosmetics.Cosmetics;
import dev.polarclient.cosmetics.RenderBatch;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

import java.util.List;

/** Polar's wings, hats and back items, drawn on the player's body and head like the game's own layers. */
public final class PolarCosmeticsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public PolarCosmeticsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
		float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible()) return;
		float moving = Math.min(1f, limbSwingAmount);
		for (int i = 0; i < 2; i++) {
			boolean head = i == 1;
			List<RenderBatch> batches = Cosmetics.models(player.getUUID(), head, moving);
			if (batches.isEmpty()) continue;
			poseStack.pushPose();
			PlayerModel<AbstractClientPlayer> model = this.getParentModel();
			(head ? model.head : model.body).translateAndRotate(poseStack);
			for (RenderBatch b : batches) CosmeticsRender.draw(poseStack, buffers, b, light);
			poseStack.popPose();
		}
	}
}
