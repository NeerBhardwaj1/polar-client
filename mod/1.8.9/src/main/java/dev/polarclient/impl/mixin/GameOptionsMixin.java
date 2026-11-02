package dev.polarclient.impl.mixin;

import dev.polarclient.impl.Keys;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

/** Adds Polar's key binds to Options > Controls (and options.txt) before the options file is read. */
@Mixin(GameOptions.class)
public abstract class GameOptionsMixin {
	@Shadow
	public KeyBinding[] allKeys;

	@Inject(method = "<init>(Lnet/minecraft/client/MinecraftClient;Ljava/io/File;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/GameOptions;load()V"))
	private void polar$keys(MinecraftClient client, File dir, CallbackInfo ci) {
		allKeys = ArrayUtils.addAll(allKeys, Keys.all());
	}
}
