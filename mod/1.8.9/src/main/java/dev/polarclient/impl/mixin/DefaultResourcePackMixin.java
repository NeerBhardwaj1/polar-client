package dev.polarclient.impl.mixin;

import net.minecraft.resource.DefaultResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

/**
 * 1.8.9 only reads "minecraft" and "realms" assets from the game's classpath; this adds Polar's own (textures, fonts,
 * language) from the mod jar. Harmless if a resource loader mod adds them too.
 */
@Mixin(DefaultResourcePack.class)
public abstract class DefaultResourcePackMixin {
	@Inject(method = "getNamespaces", at = @At("RETURN"), cancellable = true)
	private void polar$namespace(CallbackInfoReturnable<Set<String>> cir) {
		Set<String> namespaces = new HashSet<String>(cir.getReturnValue());
		namespaces.add("polarclient");
		cir.setReturnValue(namespaces);
	}
}
