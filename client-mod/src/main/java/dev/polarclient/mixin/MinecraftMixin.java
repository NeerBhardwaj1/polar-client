package dev.polarclient.mixin;

import dev.polarclient.PolarClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Window title: "Polar Client 26.3" instead of "Minecraft* 26.3". */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
	private void polar$title(CallbackInfoReturnable<String> cir) {
		if (PolarClient.modules() == null || !PolarClient.modules().client.windowTitle.on()) return;
		String title = cir.getReturnValue();
		if (title.startsWith("Minecraft")) {
			cir.setReturnValue("Polar Client" + title.substring(title.startsWith("Minecraft*") ? 10 : 9));
		}
	}
}
