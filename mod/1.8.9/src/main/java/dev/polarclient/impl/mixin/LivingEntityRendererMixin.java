package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.polarclient.cosmetics.Cosmetics;
import dev.polarclient.cosmetics.RenderBatch;
import dev.polarclient.impl.CosmeticsRender;
import dev.polarclient.impl.Tints;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.FloatBuffer;
import java.util.List;

/**
 * Hit Colour: the red, green, blue and strength of the hurt flash (the first four values the game writes). Polar
 * auras: drawn where the player stands, before the player's own turning is applied.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	private static float polar$channel(int shift, float vanilla) {
		int c = Tints.hurt;
		return c == 0 ? vanilla : (c >>> shift & 0xFF) / 255f;
	}

	@Redirect(method = "method_10252", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 0))
	private FloatBuffer polar$red(FloatBuffer buffer, float v) {
		return buffer.put(polar$channel(16, v));
	}

	@Redirect(method = "method_10252", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 1))
	private FloatBuffer polar$green(FloatBuffer buffer, float v) {
		return buffer.put(polar$channel(8, v));
	}

	@Redirect(method = "method_10252", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 2))
	private FloatBuffer polar$blue(FloatBuffer buffer, float v) {
		return buffer.put(polar$channel(0, v));
	}

	@Redirect(method = "method_10252", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 3))
	private FloatBuffer polar$strength(FloatBuffer buffer, float v) {
		return buffer.put(polar$channel(24, v));
	}

	@Inject(method = "render(Lnet/minecraft/entity/LivingEntity;DDDFF)V", at = @At("HEAD"))
	private void polar$aura(LivingEntity entity, double x, double y, double z, float yaw, float tickDelta, CallbackInfo ci) {
		if (!(entity instanceof PlayerEntity) || entity.isInvisible()) return;
		EntityRenderDispatcher d = MinecraftClient.getInstance().getEntityRenderManager();
		double wx = entity.prevX + (entity.x - entity.prevX) * tickDelta, wy = entity.prevY + (entity.y - entity.prevY) * tickDelta, wz = entity.prevZ + (entity.z - entity.prevZ) * tickDelta;
		List<RenderBatch> batches = Cosmetics.aura(entity.getUuid(), wx, wy, wz, d.yaw, d.pitch);
		if (batches.isEmpty()) return;
		GlStateManager.pushMatrix();
		GlStateManager.translate(x, y, z);
		CosmeticsRender.draw(batches, entity.getLightmapCoordinates(tickDelta), true);
		GlStateManager.popMatrix();
	}
}
