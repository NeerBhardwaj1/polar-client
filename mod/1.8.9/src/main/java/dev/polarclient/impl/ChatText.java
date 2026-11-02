package dev.polarclient.impl;

import dev.polarclient.hooks.ChatHooks;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Builds a chat line with the chat mods' additions (1.8.9 text has 16 colours and no copy click). */
public final class ChatText {
	private static final int[] RGB = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
		0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
	private static final Formatting[] COLORS = {Formatting.BLACK, Formatting.DARK_BLUE, Formatting.DARK_GREEN, Formatting.DARK_AQUA,
		Formatting.DARK_RED, Formatting.DARK_PURPLE, Formatting.GOLD, Formatting.GRAY, Formatting.DARK_GRAY, Formatting.BLUE, Formatting.GREEN,
		Formatting.AQUA, Formatting.RED, Formatting.LIGHT_PURPLE, Formatting.YELLOW, Formatting.WHITE};

	private ChatText() {
	}

	/** The nearest of the 16 chat colours. */
	public static Formatting nearest(int rgb) {
		int best = 7;
		long bestD = Long.MAX_VALUE;
		for (int i = 0; i < RGB.length; i++) {
			long dr = (rgb >> 16 & 0xFF) - (RGB[i] >> 16 & 0xFF), dg = (rgb >> 8 & 0xFF) - (RGB[i] >> 8 & 0xFF), db = (rgb & 0xFF) - (RGB[i] & 0xFF);
			long d = dr * dr + dg * dg + db * db;
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		return COLORS[best];
	}

	public static Text decorate(Text original, ChatHooks.Line line) {
		Text root = new LiteralText("");
		if (line.copyText != null) {
			// handled by ScreenMixin: copies instead of suggesting
			root.setStyle(new Style().setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, ChatHooks.COPY_PREFIX + line.copyText)));
		}
		if (line.timestamp != null) {
			Style style = new Style().setFormatting(nearest(line.timestampColor));
			if (line.copyText != null) style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText("Click to copy")));
			root.append(new LiteralText(line.timestamp).setStyle(style));
		}
		root.append(original);
		if (line.repeat > 1) root.append(new LiteralText(" (x" + line.repeat + ")").setStyle(new Style().setFormatting(Formatting.GRAY)));
		return root;
	}
}
