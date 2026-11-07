package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** XP Bottle Optimizer: an orb standing for the others in its block is drawn with the picture for their combined XP. */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin {
	@Inject(method = "getIcon", at = @At("RETURN"), cancellable = true)
	private void polar$mergedIcon(CallbackInfoReturnable<Integer> cir) {
		if (Polar.modules() == null) return;
		int icon = Polar.modules().xpBottles.orbIcon(((ExperienceOrb) (Object) this).getId(), cir.getReturnValueI());
		if (icon != cir.getReturnValueI()) cir.setReturnValue(icon);
	}
}
