package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import dev.polarclient.gui.MultiplayerView;
import dev.polarclient.gui.TitleView;
import dev.polarclient.impl.ViewScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Swaps the vanilla title and multiplayer screens for Polar's (toggles in Polar Settings). */
@Mixin(Gui.class)
public abstract class GuiMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen polar$replaceScreen(Screen screen) {
		if (Polar.modules() == null) return screen;
		boolean menu = Polar.modules().client.customMainMenu.on();
		// closing a screen outside a world goes back to the title screen
		if (screen == null && menu && Minecraft.getInstance().level == null) return new ViewScreen(new TitleView());
		if (screen == null) return null;
		if (screen.getClass() == TitleScreen.class && menu) return new ViewScreen(new TitleView());
		if (screen.getClass() == JoinMultiplayerScreen.class && Polar.modules().client.customServerList.on()) {
			return new ViewScreen(new MultiplayerView(((JoinMultiplayerScreenAccessor) screen).polar$lastScreen()));
		}
		return screen;
	}
}
