package dev.polarclient.modules.chat;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;

/** Click any chat message (with chat open) to copy its text. */
public class CopyChatModule extends Module {
	public CopyChatModule() {
		super("copy_chat", "Copy Chat", "Open chat and click a message to copy its text. Links still open as links.", Category.SOCIAL, false);
	}
}
