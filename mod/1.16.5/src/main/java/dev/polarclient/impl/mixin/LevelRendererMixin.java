package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import dev.polarclient.Polar;
import dev.polarclient.impl.Render3d;
import dev.polarclient.modules.perf.XpBottleOptimizerModule;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remembers this frame's camera so waypoints can be placed on screen, and tells XP Bottle Optimizer while an XP bottle's
 * splash (level event 2002 in a water bottle's colour) makes particles.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void polar$camera(PoseStack poseStack, float partialTicks, long nanos, boolean outline, Camera camera, GameRenderer gameRenderer,
			LightTexture lightTexture, Matrix4f projection, CallbackInfo ci) {
		Vec3 pos = camera.getPosition();
		Render3d.capture(projection, poseStack.last().pose(), pos.x, pos.y, pos.z);
	}

	@Inject(method = "levelEvent", at = @At("HEAD"))
	private void polar$splashStart(Player player, int type, BlockPos pos, int data, CallbackInfo ci) {
		if (Polar.modules() != null) Polar.modules().xpBottles.splashStart(type == 2002 && (data & 0xFFFFFF) == XpBottleOptimizerModule.XP_SPLASH_COLOR);
	}

	@Inject(method = "levelEvent", at = @At("RETURN"))
	private void polar$splashEnd(Player player, int type, BlockPos pos, int data, CallbackInfo ci) {
		if (Polar.modules() != null) Polar.modules().xpBottles.splashEnd();
	}
}
