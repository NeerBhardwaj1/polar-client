package dev.polarclient.mixin;

import dev.polarclient.PolarClient;
import dev.polarclient.modules.FreelookModule;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom (scales the FOV) and Freelook (points the camera independently of the player). */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void polar$zoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
		if (PolarClient.modules() == null) return;
		cir.setReturnValue(PolarClient.modules().zoom.modifyFov(cir.getReturnValueF()));
	}

	@ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float polar$freelookYaw(float yRot) {
		FreelookModule f = PolarClient.modules() == null ? null : PolarClient.modules().freelook;
		return f != null && f.isActive() ? f.yaw() : yRot;
	}

	@ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private float polar$freelookPitch(float xRot) {
		FreelookModule f = PolarClient.modules() == null ? null : PolarClient.modules().freelook;
		return f != null && f.isActive() ? f.pitch() : xRot;
	}
}
