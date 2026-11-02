package dev.polarclient.modules.chat;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.ColorSetting;

/** Colour and opacity of the box behind chat. */
public class ChatBackgroundModule extends Module {
	public final ColorSetting color = add(new ColorSetting("color", "Background", 0x990B1426, true));

	public ChatBackgroundModule() {
		super("chat_background", "Chat Background", "Pick the colour and opacity of the box behind chat (0% for none).", Category.SOCIAL, false);
	}
}
