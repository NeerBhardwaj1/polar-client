package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Hitboxes mod: players only, the main box in your colour, the blue look line optional. */
@Mixin(EntityHitboxDebugRenderer.class)
public abstract class EntityHitboxDebugRendererMixin {
	private static final int WHITE = -1, LOOK_BLUE = 0xFF0000FF;

	@Inject(method = "showHitboxes", at = @At("HEAD"), cancellable = true)
	private void polar$playersOnly(Entity entity, float partialTicks, boolean serverEntity, CallbackInfo ci) {
		if (Polar.modules() == null) return;
		boolean player = entity instanceof Player;
		if (Polar.modules().hitboxes.skips(player)) {
			ci.cancel();
			return;
		}
		// this version picks entities by the frustum alone, so leave out the ones Hide Far Entities isn't drawing
		if (Polar.modules().hideFarEntities.isEnabled() && entity != Minecraft.getInstance().getCameraEntity()) {
			Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
			if (Polar.modules().hideFarEntities.hides(player, entity.distanceToSqr(cam))) ci.cancel();
		}
	}

	@ModifyArg(method = "showHitboxes", at = @At(value = "INVOKE", target = "Lnet/minecraft/gizmos/GizmoStyle;stroke(I)Lnet/minecraft/gizmos/GizmoStyle;"))
	private int polar$boxColor(int color) {
		return color == WHITE && Polar.modules() != null ? Polar.modules().hitboxes.boxColor(color) : color;
	}

	@ModifyArg(method = "showHitboxes", at = @At(value = "INVOKE", target = "Lnet/minecraft/gizmos/Gizmos;point(Lnet/minecraft/world/phys/Vec3;IF)Lnet/minecraft/gizmos/GizmoProperties;"), index = 1)
	private int polar$pointColor(int color) {
		return color == WHITE && Polar.modules() != null ? Polar.modules().hitboxes.boxColor(color) : color;
	}

	@Redirect(method = "showHitboxes", at = @At(value = "INVOKE", target = "Lnet/minecraft/gizmos/Gizmos;arrow(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;I)Lnet/minecraft/gizmos/GizmoProperties;"))
	private GizmoProperties polar$lookLine(Vec3 from, Vec3 to, int color) {
		if (color == LOOK_BLUE && Polar.modules() != null && Polar.modules().hitboxes.hidesLook()) return null;
		return Gizmos.arrow(from, to, color);
	}
}
