package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.CameraView;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
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
	private static final String DRAW_BOX = "Lnet/minecraft/client/render/WorldRenderer;drawBox(Lnet/minecraft/util/math/Box;IIII)V";
	private static final String VERTEX = "Lnet/minecraft/client/render/BufferBuilder;vertex(DDD)Lnet/minecraft/client/render/BufferBuilder;";

	@Unique
	private double polar$lookX, polar$lookY, polar$lookZ;

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void polar$hideFar(Entity entity, CameraView camera, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
		if (Polar.modules() == null) return;
		if (entity instanceof ExperienceOrbEntity && Polar.modules().xpBottles.hidesOrb(entity.getEntityId())) {
			cir.setReturnValue(false);
			return;
		}
		if (!Polar.modules().hideFarEntities.isEnabled()) return;
		if (entity == MinecraftClient.getInstance().getCameraEntity()) return;
		if (Polar.modules().hideFarEntities.hides(entity instanceof PlayerEntity, entity.squaredDistanceTo(camX, camY, camZ))) cir.setReturnValue(false);
	}

	@Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true)
	private void polar$playersOnly(Entity entity, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
		if (Polar.modules() != null && Polar.modules().hitboxes.skips(entity instanceof PlayerEntity)) ci.cancel();
	}

	@ModifyArg(method = "renderHitbox", at = @At(value = "INVOKE", target = DRAW_BOX, ordinal = 0), index = 1)
	private int polar$red(int v) {
		return polar$channel(v, 16);
	}

	@ModifyArg(method = "renderHitbox", at = @At(value = "INVOKE", target = DRAW_BOX, ordinal = 0), index = 2)
	private int polar$green(int v) {
		return polar$channel(v, 8);
	}

	@ModifyArg(method = "renderHitbox", at = @At(value = "INVOKE", target = DRAW_BOX, ordinal = 0), index = 3)
	private int polar$blue(int v) {
		return polar$channel(v, 0);
	}

	@ModifyArg(method = "renderHitbox", at = @At(value = "INVOKE", target = DRAW_BOX, ordinal = 0), index = 4)
	private int polar$alpha(int v) {
		return polar$channel(v, 24);
	}

	@Unique
	private static int polar$channel(int vanilla, int shift) {
		if (Polar.modules() == null || !Polar.modules().hitboxes.isEnabled()) return vanilla;
		return Polar.modules().hitboxes.boxColor(-1) >>> shift & 255;
	}

	/** The look line runs from the first vertex to the second; when it's off, both are the same point. */
	@Redirect(method = "renderHitbox", at = @At(value = "INVOKE", target = VERTEX, ordinal = 0))
	private BufferBuilder polar$lookStart(BufferBuilder buffer, double x, double y, double z) {
		polar$lookX = x;
		polar$lookY = y;
		polar$lookZ = z;
		return buffer.vertex(x, y, z);
	}

	@Redirect(method = "renderHitbox", at = @At(value = "INVOKE", target = VERTEX, ordinal = 1))
	private BufferBuilder polar$lookEnd(BufferBuilder buffer, double x, double y, double z) {
		if (Polar.modules() != null && Polar.modules().hitboxes.hidesLook()) return buffer.vertex(polar$lookX, polar$lookY, polar$lookZ);
		return buffer.vertex(x, y, z);
	}
}
