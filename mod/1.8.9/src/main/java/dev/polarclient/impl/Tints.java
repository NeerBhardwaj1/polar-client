package dev.polarclient.impl;

/** Hit Colour and Glint Colour on 1.8.9: the colours the game's renderers read (see the mixins). */
public final class Tints {
	/** ARGB of the hurt flash (alpha = strength); 0 = the game's own. */
	public static volatile int hurt;
	/** RGB of the glint; -1 = the game's own. */
	public static volatile int glint = -1;

	private Tints() {
	}
}
