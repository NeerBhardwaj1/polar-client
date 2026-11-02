package dev.polarclient.modules.hud;

import dev.polarclient.module.TextHudModule;
import dev.polarclient.setting.BooleanSetting;
import net.minecraft.client.Minecraft;

import java.util.List;

public class FpsModule extends TextHudModule {
	private final BooleanSetting label = add(new BooleanSetting("label", "Show \"FPS\" label", true));

	public FpsModule() {
		super("fps", "FPS", "Shows your frames per second.", true, 4, 24);
	}

	@Override
	protected List<String> lines(boolean preview) {
		int fps = Minecraft.getInstance().getFps();
		return List.of(label.on() ? fps + " FPS" : String.valueOf(fps));
	}
}
