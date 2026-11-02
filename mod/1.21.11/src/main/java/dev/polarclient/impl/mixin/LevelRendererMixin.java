package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.polarclient.impl.Render3d;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers this frame's camera so waypoints can be placed on screen. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void polar$camera(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderOutline, Camera camera, Matrix4f view,
			Matrix4f projection, Matrix4f cullProjection, GpuBufferSlice fog, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
		Vec3 pos = camera.position();
		Render3d.capture(projection, view, pos.x, pos.y, pos.z);
	}
}
