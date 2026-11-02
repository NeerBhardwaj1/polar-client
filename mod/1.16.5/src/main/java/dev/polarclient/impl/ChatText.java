package dev.polarclient.impl;

import dev.polarclient.hooks.ChatHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.TextComponent;

/** Builds a chat line with the chat mods' additions. */
public final class ChatText {
	private ChatText() {
	}

	public static Component decorate(Component original, ChatHooks.Line line) {
		MutableComponent root = new TextComponent("");
		if (line.copyText != null) {
			// parts of the message with their own click (links) keep it; everything else copies
			root.setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, line.copyText)));
		}
		if (line.timestamp != null) {
			Style style = Style.EMPTY.withColor(TextColor.fromRgb(line.timestampColor));
			if (line.copyText != null) style = style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent("Click to copy")));
			root.append(new TextComponent(line.timestamp).setStyle(style));
		}
		root.append(original);
		if (line.repeat > 1) root.append(new TextComponent(" (x" + line.repeat + ")").withStyle(ChatFormatting.GRAY));
		return root;
	}
}
