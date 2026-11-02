package dev.polarclient.impl.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads where the vanilla server list would go back to, so Polar's list returns to the same place. */
@Mixin(MultiplayerScreen.class)
public interface MultiplayerScreenAccessor {
	@Accessor("parent")
	Screen polar$parent();
}
