package dev.polarclient.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.polarclient.cosmetics.Cosmetics;
import dev.polarclient.cosmetics.RenderBatch;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

import java.util.List;

/** Polar's wings, hats and back items, drawn on the player's body and head like the game's own layers. */
public final class PolarCosmeticsFeature implements FeatureRenderer<AbstractClientPlayerEntity> {
	private final PlayerEntityRenderer renderer;

	public PolarCosmeticsFeature(PlayerEntityRenderer renderer) {
		this.renderer = renderer;
	}

	@Override
	public void render(AbstractClientPlayerEntity player, float limbAngle, float limbDistance, float tickDelta, float age, float headYaw, float headPitch, float scale) {
		if (player.isInvisible()) return;
		float moving = Math.min(1f, limbDistance);
		int light = player.getLightmapCoordinates(tickDelta);
		for (int i = 0; i < 2; i++) {
			boolean head = i == 1;
			List<RenderBatch> batches = Cosmetics.models(player.getUuid(), head, moving);
			if (batches.isEmpty()) continue;
			GlStateManager.pushMatrix();
			PlayerEntityModel model = renderer.getModel();
			(head ? model.head : model.body).preRender(0.0625f);
			CosmeticsRender.draw(batches, light, false);
			GlStateManager.popMatrix();
		}
	}

	@Override
	public boolean combineTextures() {
		return false;
	}
}
