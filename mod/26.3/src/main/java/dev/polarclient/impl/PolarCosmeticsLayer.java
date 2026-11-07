package dev.polarclient.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.polarclient.cosmetics.Cosmetics;
import dev.polarclient.cosmetics.RenderBatch;
import dev.polarclient.cosmetics.WingPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import java.util.List;
import java.util.UUID;

/** Polar's wings, hats and back items, drawn on the player's body and head like the game's own layers. */
public final class PolarCosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public PolarCosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible) return;
		UUID id = CosmeticsRender.uuid(state.id);
		if (id == null) return;
		WingPose pose = CosmeticsRender.pose(state.id, Math.min(1f, state.walkAnimationSpeed));
		for (int i = 0; i < 2; i++) {
			boolean head = i == 1;
			List<RenderBatch> batches = Cosmetics.models(id, head, pose);
			if (batches.isEmpty()) continue;
			poseStack.pushPose();
			PlayerModel model = this.getParentModel();
			(head ? model.head : model.body).translateAndRotate(poseStack);
			for (RenderBatch b : batches) CosmeticsRender.submit(poseStack, collector, b, light);
			poseStack.popPose();
		}
	}
}
