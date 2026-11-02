package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.polarclient.hud.HudRenderer;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** HUD Tweaks: hide boss bars. */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void polar$bossBar(PoseStack pose, CallbackInfo ci) {
		if (HudRenderer.hideBossBar()) ci.cancel();
	}
}
