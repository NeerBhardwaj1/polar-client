package dev.polarclient.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.polarclient.cosmetics.Cosmetics;
import dev.polarclient.cosmetics.RenderBatch;
import dev.polarclient.cosmetics.WingPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

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
		WingPose pose = WingPose.walking(Math.min(1f, limbSwingAmount));
		pose.sprinting = player.isSprinting();
		pose.sneaking = player.isCrouching();
		pose.onGround = player.isOnGround();
		pose.flying = player.abilities.flying;
		pose.gliding = player.isFallFlying();
		pose.swimming = player.isSwimming();
		pose.riding = player.isPassenger();
		pose.sleeping = player.isSleeping();
		pose.elytra = player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
		pose.bodyYaw = player.yBodyRot;
		Entity cam = Minecraft.getInstance().getCameraEntity();
		pose.distanceSq = cam == null || cam == player ? 0 : cam.distanceToSqr(player);
		for (int i = 0; i < 2; i++) {
			boolean head = i == 1;
			List<RenderBatch> batches = Cosmetics.models(player.getUUID(), head, pose);
			if (batches.isEmpty()) continue;
			poseStack.pushPose();
			PlayerModel<AbstractClientPlayer> model = this.getParentModel();
			(head ? model.head : model.body).translateAndRotate(poseStack);
			for (RenderBatch b : batches) CosmeticsRender.draw(poseStack, buffers, b, light);
			poseStack.popPose();
		}
	}
}
