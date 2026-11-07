package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** XP Bottle Optimizer: fewer (or no) XP orb pickup sounds. */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
	@Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
	private void polar$xpSounds(SoundInstance sound, CallbackInfo ci) {
		if (Polar.modules() == null || sound == null) return;
		if (!Polar.modules().xpBottles.allowSound(String.valueOf(sound.getIdentifier()))) ci.cancel();
	}
}
