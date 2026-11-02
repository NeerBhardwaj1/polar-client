package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import dev.polarclient.impl.Render3d;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers this frame's camera so waypoints can be placed on screen. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void polar$camera(PoseStack poseStack, float partialTicks, long nanos, boolean outline, Camera camera, GameRenderer gameRenderer,
			LightTexture lightTexture, Matrix4f projection, CallbackInfo ci) {
		Vec3 pos = camera.getPosition();
		Render3d.capture(projection, poseStack.last().pose(), pos.x, pos.y, pos.z);
	}
}
