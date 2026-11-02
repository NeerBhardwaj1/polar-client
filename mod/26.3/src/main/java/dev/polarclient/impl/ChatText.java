package dev.polarclient.impl;

import dev.polarclient.hooks.ChatHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Builds a chat line with the chat mods' additions. */
public final class ChatText {
	private ChatText() {
	}

	public static Component decorate(Component original, ChatHooks.Line line) {
		MutableComponent root = Component.empty();
		if (line.copyText != null) {
			// parts of the message with their own click (links) keep it; everything else copies
			root.setStyle(Style.EMPTY.withClickEvent(new ClickEvent.CopyToClipboard(line.copyText)));
		}
		if (line.timestamp != null) {
			MutableComponent stamp = Component.literal(line.timestamp).withColor(line.timestampColor);
			if (line.copyText != null) stamp.withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy"))));
			root.append(stamp);
		}
		root.append(original);
		if (line.repeat > 1) root.append(Component.literal(" (x" + line.repeat + ")").withStyle(ChatFormatting.GRAY));
		return root;
	}
}
