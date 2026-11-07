package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import dev.polarclient.Polar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hide Far Entities, and the Hitboxes mod: players only, the box in your colour, the blue look line optional. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	private static final String RENDER_BOX = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderBox(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/Entity;FFF)V";
	private static final String VERTEX = "Lcom/mojang/blaze3d/vertex/VertexConsumer;vertex(Lcom/mojang/math/Matrix4f;FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;";

	@Unique
	private float polar$lookX, polar$lookY, polar$lookZ;

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void polar$hideFar(Entity entity, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
		if (Polar.modules() == null) return;
		if (entity instanceof ExperienceOrb && Polar.modules().xpBottles.hidesOrb(entity.getId())) {
			cir.setReturnValue(false);
			return;
		}
		if (!Polar.modules().hideFarEntities.isEnabled()) return;
		if (entity == Minecraft.getInstance().getCameraEntity()) return;
		if (Polar.modules().hideFarEntities.hides(entity instanceof Player, entity.distanceToSqr(camX, camY, camZ))) cir.setReturnValue(false);
	}

	@Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true)
	private void polar$playersOnly(PoseStack poseStack, VertexConsumer buffer, Entity entity, float partialTicks, CallbackInfo ci) {
		if (Polar.modules() != null && Polar.modules().hitboxes.skips(entity instanceof Player)) ci.cancel();
	}

	@ModifyArg(method = "renderHitbox", at = @At(value = "INVOKE", target = RENDER_BOX, ordinal = 0), index = 3)
	private float polar$red(float v) {
		return polar$channel(v, 16);
	}

	@ModifyArg(method = "renderHitbox", at = @At(value = "INVOKE", target = RENDER_BOX, ordinal = 0), index = 4)
	private float polar$green(float v) {
		return polar$channel(v, 8);
	}

	@ModifyArg(method = "renderHitbox", at = @At(value = "INVOKE", target = RENDER_BOX, ordinal = 0), index = 5)
	private float polar$blue(float v) {
		return polar$channel(v, 0);
	}

	@Unique
	private static float polar$channel(float vanilla, int shift) {
		if (Polar.modules() == null || !Polar.modules().hitboxes.isEnabled()) return vanilla;
		return (Polar.modules().hitboxes.boxColor(-1) >> shift & 255) / 255f;
	}

	/** The look line runs from the first vertex to the second; when it's off, both are the same point. */
	@Redirect(method = "renderHitbox", at = @At(value = "INVOKE", target = VERTEX, ordinal = 0))
	private VertexConsumer polar$lookStart(VertexConsumer buffer, Matrix4f m, float x, float y, float z) {
		polar$lookX = x;
		polar$lookY = y;
		polar$lookZ = z;
		return buffer.vertex(m, x, y, z);
	}

	@Redirect(method = "renderHitbox", at = @At(value = "INVOKE", target = VERTEX, ordinal = 1))
	private VertexConsumer polar$lookEnd(VertexConsumer buffer, Matrix4f m, float x, float y, float z) {
		if (Polar.modules() != null && Polar.modules().hitboxes.hidesLook()) return buffer.vertex(m, polar$lookX, polar$lookY, polar$lookZ);
		return buffer.vertex(m, x, y, z);
	}
}
