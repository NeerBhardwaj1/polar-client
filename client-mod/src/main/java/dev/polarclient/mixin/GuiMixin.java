package dev.polarclient.mixin;

import dev.polarclient.PolarClient;
import dev.polarclient.gui.PolarMultiplayerScreen;
import dev.polarclient.gui.PolarTitleScreen;
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
	private Screen polar$mainMenu(Screen screen) {
		if (screen != null && screen.getClass() == TitleScreen.class
			&& PolarClient.modules() != null && PolarClient.modules().client.customMainMenu.on()) {
			return new PolarTitleScreen();
		}
		if (screen != null && screen.getClass() == JoinMultiplayerScreen.class
			&& PolarClient.modules() != null && PolarClient.modules().client.customServerList.on()) {
			return new PolarMultiplayerScreen(((JoinMultiplayerScreenAccessor) screen).polar$lastScreen());
		}
		return screen;
	}
}
