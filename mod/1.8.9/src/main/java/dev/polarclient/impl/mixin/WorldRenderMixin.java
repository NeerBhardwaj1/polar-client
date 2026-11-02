package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.polarclient.Polar;
import dev.polarclient.impl.Render3d;
import net.minecraft.block.material.Material;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clear Water (thin fog under water) and the camera for waypoints. */
@Mixin(GameRenderer.class)
public abstract class WorldRenderMixin {
	@Inject(method = "renderFog", at = @At("TAIL"))
	private void polar$clearWater(int pass, float tickDelta, CallbackInfo ci) {
		if (Polar.modules() == null || !Polar.modules().clearWater.isEnabled()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		Entity camera = mc.getCameraEntity();
		if (mc.world == null || camera == null) return;
		if (Camera.getSubmergedBlock(mc.world, camera, tickDelta).getMaterial() == Material.WATER) GlStateManager.fogDensity(0.004f);
	}

	@Inject(method = "setupCamera", at = @At("TAIL"))
	private void polar$camera(float tickDelta, int pass, CallbackInfo ci) {
		Entity e = MinecraftClient.getInstance().getCameraEntity();
		if (e == null) return;
		Render3d.capture(e.prevTickX + (e.x - e.prevTickX) * tickDelta, e.prevTickY + (e.y - e.prevTickY) * tickDelta, e.prevTickZ + (e.z - e.prevTickZ) * tickDelta);
	}
}
