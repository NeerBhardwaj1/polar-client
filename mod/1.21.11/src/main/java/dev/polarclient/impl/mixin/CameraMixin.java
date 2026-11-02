package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import dev.polarclient.modules.FreelookModule;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Freelook: points the camera independently of the player. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float polar$freelookYaw(float yRot) {
		FreelookModule f = Polar.modules() == null ? null : Polar.modules().freelook;
		return f != null && f.isActive() ? f.yaw() : yRot;
	}

	@ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private float polar$freelookPitch(float xRot) {
		FreelookModule f = Polar.modules() == null ? null : Polar.modules().freelook;
		return f != null && f.isActive() ? f.pitch() : xRot;
	}
}
