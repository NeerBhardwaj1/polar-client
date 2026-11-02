package dev.polarclient.impl.mixin;

import dev.polarclient.impl.Tints;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.FloatBuffer;

/** Hit Colour: the red, green, blue and strength of the hurt flash (the first four values the game writes). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	private static float polar$channel(int shift, float vanilla) {
		int c = Tints.hurt;
		return c == 0 ? vanilla : (c >>> shift & 0xFF) / 255f;
	}

	@Redirect(method = "method_10252", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 0))
	private FloatBuffer polar$red(FloatBuffer buffer, float v) {
		return buffer.put(polar$channel(16, v));
	}

	@Redirect(method = "method_10252", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 1))
	private FloatBuffer polar$green(FloatBuffer buffer, float v) {
		return buffer.put(polar$channel(8, v));
	}

	@Redirect(method = "method_10252", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 2))
	private FloatBuffer polar$blue(FloatBuffer buffer, float v) {
		return buffer.put(polar$channel(0, v));
	}

	@Redirect(method = "method_10252", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 3))
	private FloatBuffer polar$strength(FloatBuffer buffer, float v) {
		return buffer.put(polar$channel(24, v));
	}
}
