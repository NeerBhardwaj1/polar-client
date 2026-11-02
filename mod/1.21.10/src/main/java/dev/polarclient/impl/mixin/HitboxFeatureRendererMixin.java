package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.polarclient.Polar;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.feature.HitboxFeatureRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The Hitboxes mod: the white box in your colour, the blue look line optional. */
@Mixin(HitboxFeatureRenderer.class)
public abstract class HitboxFeatureRendererMixin {
	private static final int LOOK_BLUE = 0xFF0000FF;

	@Redirect(method = "renderHitbox", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShapeRenderer;renderLineBox(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;DDDDDDFFFF)V"))
	private static void polar$boxColor(PoseStack.Pose pose, VertexConsumer buffer, double x0, double y0, double z0, double x1, double y1, double z1, float r, float g, float b, float a) {
		if (r == 1f && g == 1f && b == 1f && Polar.modules() != null && Polar.modules().hitboxes.isEnabled()) {
			int c = Polar.modules().hitboxes.boxColor(-1);
			r = (c >> 16 & 255) / 255f;
			g = (c >> 8 & 255) / 255f;
			b = (c & 255) / 255f;
			a = (c >>> 24) / 255f;
		}
		ShapeRenderer.renderLineBox(pose, buffer, x0, y0, z0, x1, y1, z1, r, g, b, a);
	}

	@Redirect(method = "renderHitboxesAndViewVector", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShapeRenderer;renderVector(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Vector3f;Lnet/minecraft/world/phys/Vec3;I)V"))
	private static void polar$lookLine(PoseStack pose, VertexConsumer buffer, Vector3f from, Vec3 direction, int color) {
		if (color == LOOK_BLUE && Polar.modules() != null && Polar.modules().hitboxes.hidesLook()) return;
		ShapeRenderer.renderVector(pose, buffer, from, direction, color);
	}
}
