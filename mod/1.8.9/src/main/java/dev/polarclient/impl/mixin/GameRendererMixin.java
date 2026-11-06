package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import dev.polarclient.impl.MotionBlur;
import dev.polarclient.modules.FreelookModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Zoom (scales the FOV), No Hurt Cam (scales the 14 degree hurt shake), Fullbright (lightmap built as if brightness
 * were far past "Bright"), Freelook (the camera reads Freelook's angles instead of your player's) and Motion Blur
 * (drawn after the world, before the hand and the HUD).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow private MinecraftClient client;

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void polar$zoom(float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
		if (Polar.modules() == null || !useFovSetting) return;
		cir.setReturnValue(Polar.modules().zoom.modifyFov(cir.getReturnValueF()));
	}

	@ModifyConstant(method = "bobViewWhenHurt", constant = @Constant(floatValue = 14.0f))
	private float polar$hurtShake(float degrees) {
		return Polar.modules() == null ? degrees : degrees * Polar.modules().noHurtCam.shakeScale();
	}

	@Redirect(method = "updateLightmap", at = @At(value = "FIELD", target = "Lnet/minecraft/client/option/GameOptions;gamma:F", opcode = 180))
	private float polar$gamma(GameOptions options) {
		return Polar.modules() != null && Polar.modules().fullbright.isEnabled() ? 16.0f : options.gamma;
	}

	private static FreelookModule polar$freelook() {
		FreelookModule f = Polar.modules() == null ? null : Polar.modules().freelook;
		return f != null && f.isActive() ? f : null;
	}

	@Redirect(method = "transformCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;yaw:F", opcode = 180))
	private float polar$yaw(Entity entity) {
		FreelookModule f = polar$freelook();
		return f != null ? f.yaw() : entity.yaw;
	}

	@Redirect(method = "transformCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevYaw:F", opcode = 180))
	private float polar$prevYaw(Entity entity) {
		FreelookModule f = polar$freelook();
		return f != null ? f.yaw() : entity.prevYaw;
	}

	@Redirect(method = "transformCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;pitch:F", opcode = 180))
	private float polar$pitch(Entity entity) {
		FreelookModule f = polar$freelook();
		return f != null ? f.pitch() : entity.pitch;
	}

	@Redirect(method = "transformCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevPitch:F", opcode = 180))
	private float polar$prevPitch(Entity entity) {
		FreelookModule f = polar$freelook();
		return f != null ? f.pitch() : entity.prevPitch;
	}

	@Shadow
	private float getFov(float tickDelta, boolean changingFov) {
		throw new AssertionError();
	}

	@Inject(method = "renderWorld(IFJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;renderHand(FI)V"))
	private void polar$motionBlur(int pass, float tickDelta, long limitTime, CallbackInfo ci) {
		FreelookModule f = polar$freelook();
		float yaw, pitch;
		if (f != null) {
			yaw = f.yaw();
			pitch = f.pitch();
		} else {
			Entity e = this.client.getCameraEntity();
			if (e == null) return;
			yaw = e.prevYaw + (e.yaw - e.prevYaw) * tickDelta;
			pitch = e.prevPitch + (e.pitch - e.prevPitch) * tickDelta;
		}
		MotionBlur.render(this.client, yaw, pitch, this.getFov(tickDelta, true), tickDelta);
	}
}
