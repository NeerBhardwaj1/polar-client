package dev.polarclient.util;

import dev.polarclient.platform.GameKey;
import dev.polarclient.platform.Platform;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Counts left/right clicks per second by watching the attack/use keys every frame
 * (no input hooks needed, so it works with any key or mouse button bound to them).
 */
public final class ClickTracker {
	private static final Deque<Long> LEFT = new ArrayDeque<Long>();
	private static final Deque<Long> RIGHT = new ArrayDeque<Long>();
	private static boolean leftWasDown, rightWasDown;

	private ClickTracker() {
	}

	/** Call once per rendered frame. */
	public static void update() {
		Platform p = Platform.get();
		long now = System.currentTimeMillis();
		boolean inGame = p.hasPlayer() && !p.screenOpen();
		boolean left = inGame && p.keyDown(GameKey.ATTACK);
		boolean right = inGame && p.keyDown(GameKey.USE);
		if (left && !leftWasDown) LEFT.addLast(now);
		if (right && !rightWasDown) RIGHT.addLast(now);
		leftWasDown = left;
		rightWasDown = right;
		prune(LEFT, now);
		prune(RIGHT, now);
	}

	private static void prune(Deque<Long> clicks, long now) {
		while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000) clicks.pollFirst();
	}

	public static int leftCps() {
		return LEFT.size();
	}

	public static int rightCps() {
		return RIGHT.size();
	}
}
