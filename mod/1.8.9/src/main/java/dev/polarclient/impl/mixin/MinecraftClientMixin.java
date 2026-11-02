package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import dev.polarclient.gui.MultiplayerView;
import dev.polarclient.gui.TitleView;
import dev.polarclient.impl.ViewScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Polar's title and server list screens, the client tick, saving on exit and the window title. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen polar$replaceScreen(Screen screen) {
		if (Polar.modules() == null) return screen;
		MinecraftClient mc = (MinecraftClient) (Object) this;
		boolean menu = Polar.modules().client.customMainMenu.on();
		if (menu && (screen == null && mc.world == null || screen != null && screen.getClass() == TitleScreen.class)) {
			// what vanilla does when the title screen opens
			mc.options.debugEnabled = false;
			if (mc.inGameHud != null) mc.inGameHud.getChatHud().clear();
			return new ViewScreen(new TitleView());
		}
		if (screen != null && screen.getClass() == MultiplayerScreen.class && Polar.modules().client.customServerList.on()) {
			return new ViewScreen(new MultiplayerView(((MultiplayerScreenAccessor) screen).polar$parent()));
		}
		return screen;
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void polar$tick(CallbackInfo ci) {
		Polar.tick();
		if (Polar.modules() == null) return;
		MinecraftClient mc = (MinecraftClient) (Object) this;
		// a title screen opened before Polar was ready
		if (mc.currentScreen != null && mc.currentScreen.getClass() == TitleScreen.class && Polar.modules().client.customMainMenu.on()) {
			mc.setScreen(new ViewScreen(new TitleView()));
		}
		String title = Polar.modules().client.windowTitle.on() ? "Polar Client 1.8.9" : "Minecraft 1.8.9";
		if (!title.equals(Display.getTitle())) Display.setTitle(title);
	}

	@Inject(method = "stop", at = @At("HEAD"))
	private void polar$save(CallbackInfo ci) {
		Polar.saveConfig();
	}
}
