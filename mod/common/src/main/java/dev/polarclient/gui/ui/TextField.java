package dev.polarclient.gui.ui;

import dev.polarclient.gfx.Gfx;
import dev.polarclient.gui.KeyInput;
import dev.polarclient.platform.Platform;
import dev.polarclient.util.Draw;

/**
 * A borderless one-line text input in Minecraft's font (the screens draw the box around it), with a blinking cursor,
 * select-all, copy/paste and horizontal scrolling. The same on every Minecraft version.
 */
public final class TextField {
	public interface Listener {
		void changed(String value);
	}

	private String value = "";
	private int cursor;
	private boolean allSelected;
	private boolean focused;
	private int maxLength = 32;
	private int textColor = 0xFFFFFFFF;
	private Listener listener;
	private int x, y, width = 100;
	private int scrollPx;
	private long focusedAt;
	public boolean visible = true;

	public TextField(int maxLength) {
		this.maxLength = maxLength;
	}

	public String value() {
		return value;
	}

	public void setValue(String v) {
		String s = v == null ? "" : v;
		if (s.length() > maxLength) s = s.substring(0, maxLength);
		value = s;
		cursor = s.length();
		allSelected = false;
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	public void setTextColor(int color) {
		textColor = color;
	}

	public void setBounds(int x, int y, int width) {
		this.x = x;
		this.y = y;
		this.width = width;
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public boolean focused() {
		return focused;
	}

	public void setFocused(boolean focused) {
		if (focused && !this.focused) focusedAt = System.currentTimeMillis();
		this.focused = focused;
		if (!focused) allSelected = false;
	}

	private void changed() {
		if (listener != null) listener.changed(value);
	}

	private void insert(String text) {
		StringBuilder clean = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c >= ' ' && c != 127 && c != '§') clean.append(c);
		}
		if (allSelected) {
			value = "";
			cursor = 0;
			allSelected = false;
		}
		String next = value.substring(0, cursor) + clean + value.substring(cursor);
		if (next.length() > maxLength) {
			int room = maxLength - value.length();
			if (room <= 0) return;
			clean.setLength(room);
			next = value.substring(0, cursor) + clean + value.substring(cursor);
		}
		value = next;
		cursor += clean.length();
		changed();
	}

	/** Clicks inside focus the field (and move the cursor); clicks outside unfocus it. Returns true if inside. */
	public boolean mouseClicked(double mx, double my) {
		boolean inside = visible && mx >= x - 2 && mx < x + width + 2 && my >= y - 3 && my < y + 12;
		setFocused(inside);
		if (inside) {
			allSelected = false;
			int rel = (int) Math.round(mx - x + scrollPx);
			String fit = Platform.get().trimToWidth(value, Math.max(0, rel));
			cursor = fit.length();
		}
		return inside;
	}

	public boolean keyPressed(KeyInput k) {
		if (!focused) return false;
		switch (k.key) {
			case BACKSPACE:
				if (allSelected) {
					setValue("");
					changed();
				} else if (cursor > 0) {
					value = value.substring(0, cursor - 1) + value.substring(cursor);
					cursor--;
					changed();
				}
				return true;
			case DELETE:
				if (allSelected) {
					setValue("");
					changed();
				} else if (cursor < value.length()) {
					value = value.substring(0, cursor) + value.substring(cursor + 1);
					changed();
				}
				return true;
			case LEFT:
				allSelected = false;
				cursor = Math.max(0, cursor - 1);
				return true;
			case RIGHT:
				allSelected = false;
				cursor = Math.min(value.length(), cursor + 1);
				return true;
			case HOME:
				allSelected = false;
				cursor = 0;
				return true;
			case END:
				allSelected = false;
				cursor = value.length();
				return true;
			case A:
				if (k.control) {
					allSelected = true;
					cursor = value.length();
					return true;
				}
				return false;
			case C:
				if (k.control) {
					Platform.get().setClipboard(value);
					return true;
				}
				return false;
			case X:
				if (k.control) {
					Platform.get().setClipboard(value);
					setValue("");
					changed();
					return true;
				}
				return false;
			case V:
				if (k.control) {
					insert(Platform.get().clipboard());
					return true;
				}
				return false;
			case ESCAPE:
			case ENTER:
			case TAB:
				return false;
			default:
				// letters etc. arrive through charTyped; swallow so they don't also trigger shortcuts
				return !k.control;
		}
	}

	public boolean charTyped(char c) {
		if (!focused) return false;
		insert(String.valueOf(c));
		return true;
	}

	public void render(Gfx g) {
		if (!visible) return;
		int cursorPx = Draw.width(value.substring(0, cursor));
		if (cursorPx - scrollPx > width - 2) scrollPx = cursorPx - width + 2;
		if (cursorPx - scrollPx < 0) scrollPx = cursorPx;
		scrollPx = Math.max(0, scrollPx);
		g.scissor(x, y - 2, x + width, y + 11);
		if (allSelected && !value.isEmpty()) {
			g.fill(x - scrollPx, y - 1, x - scrollPx + Draw.width(value), y + 9, 0x803A7BD5);
		}
		g.text(value, x - scrollPx, y, textColor, true);
		boolean blink = (System.currentTimeMillis() - focusedAt) / 500 % 2 == 0;
		if (focused && blink) {
			int cx = x - scrollPx + cursorPx;
			g.fill(cx, y - 1, cx + 1, y + 9, 0xFFD0D0D0);
		}
		g.endScissor();
	}
}
