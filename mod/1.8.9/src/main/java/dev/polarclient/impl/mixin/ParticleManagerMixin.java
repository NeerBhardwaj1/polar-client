package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Particles: drops part of the new particles. */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
	@Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
	private void polar$fewerParticles(Particle particle, CallbackInfo ci) {
		if (Polar.modules() != null && Polar.modules().particles.isEnabled() && Polar.modules().particles.drop()) ci.cancel();
	}
}
