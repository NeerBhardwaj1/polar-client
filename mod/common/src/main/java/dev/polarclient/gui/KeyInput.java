package dev.polarclient.gui;

/** A key press, translated by the version into the keys Polar's views care about. */
public final class KeyInput {
	public enum Key { ESCAPE, ENTER, BACKSPACE, DELETE, LEFT, RIGHT, UP, DOWN, HOME, END, TAB, F5, A, C, V, X, OTHER }

	public final Key key;
	public final boolean shift, control;
	/** The key is bound to "Open Mod Menu" (Right Shift by default). */
	public final boolean menuKey;

	public KeyInput(Key key, boolean shift, boolean control, boolean menuKey) {
		this.key = key;
		this.shift = shift;
		this.control = control;
		this.menuKey = menuKey;
	}

	public boolean is(Key k) {
		return key == k;
	}

	public boolean confirm() {
		return key == Key.ENTER;
	}
}
