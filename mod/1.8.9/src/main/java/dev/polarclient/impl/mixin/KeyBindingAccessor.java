package dev.polarclient.impl.mixin;

import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Toggle Sprint / Sneak hold the sprint and sneak keys down themselves on 1.8.9, which has no toggle options. */
@Mixin(KeyBinding.class)
public interface KeyBindingAccessor {
	@Accessor("pressed")
	void polar$setPressed(boolean pressed);
}
