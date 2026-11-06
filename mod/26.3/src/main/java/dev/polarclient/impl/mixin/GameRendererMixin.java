package dev.polarclient.impl.mixin;

import dev.polarclient.impl.MotionBlur;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Motion Blur: adds Polar's post effect to the ones the game draws this frame. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow @Final private Minecraft minecraft;
	@Shadow @Final private List<Identifier> requestedPostEffects;
	@Shadow @Final private List<Identifier> failedPostEffects;

	@Inject(method = "update", at = @At("TAIL"))
	private void polar$motionBlur(DeltaTracker deltaTracker, CallbackInfo ci) {
		MotionBlur.request(this.minecraft, this.requestedPostEffects, this.failedPostEffects);
	}
}
