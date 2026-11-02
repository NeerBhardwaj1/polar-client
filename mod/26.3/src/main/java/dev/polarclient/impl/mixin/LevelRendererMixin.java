package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import dev.polarclient.impl.Render3d;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers this frame's camera so waypoints can be placed on screen. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void polar$camera(GraphicsResourceAllocator allocator, boolean renderOutline, CameraRenderState cameraState, GpuBufferSlice terrainFog,
			Vector4f fogColor, boolean shouldRenderSky, boolean consistentDepthRequired, CallbackInfo ci) {
		Render3d.capture(cameraState.projectionMatrix, cameraState.viewRotationMatrix, cameraState.pos.x, cameraState.pos.y, cameraState.pos.z);
	}
}
