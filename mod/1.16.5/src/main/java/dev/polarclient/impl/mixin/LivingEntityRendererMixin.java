package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.polarclient.cosmetics.Cosmetics;
import dev.polarclient.cosmetics.RenderBatch;
import dev.polarclient.impl.CosmeticsRender;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Polar auras: drawn where the player stands, before the player's own turning is applied. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
	private void polar$aura(LivingEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (!(entity instanceof Player) || entity.isInvisible()) return;
		Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
		double x = Mth.lerp(partialTicks, entity.xo, entity.getX()), y = Mth.lerp(partialTicks, entity.yo, entity.getY()), z = Mth.lerp(partialTicks, entity.zo, entity.getZ());
		for (RenderBatch b : Cosmetics.aura(entity.getUUID(), x, y, z, cam.getYRot(), cam.getXRot())) CosmeticsRender.draw(poseStack, buffers, b, light);
	}
}
