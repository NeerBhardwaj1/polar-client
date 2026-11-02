package dev.polarclient.modules.chat;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;

/** The same message twice in a row becomes one line with a counter. */
public class CompactChatModule extends Module {
	public CompactChatModule() {
		super("compact_chat", "Compact Chat", "Stacks repeated messages into one line with a counter, like \"Saved (x3)\".", Category.SOCIAL, false);
	}
}
