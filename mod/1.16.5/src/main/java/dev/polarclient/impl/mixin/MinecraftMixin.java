package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Window title: "Polar Client 1.16.5" instead of "Minecraft* 1.16.5". */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
	private void polar$title(CallbackInfoReturnable<String> cir) {
		if (Polar.modules() == null || !Polar.modules().client.windowTitle.on()) return;
		String title = cir.getReturnValue();
		if (title.startsWith("Minecraft")) {
			cir.setReturnValue("Polar Client" + title.substring(title.startsWith("Minecraft*") ? 10 : 9));
		}
	}
}
