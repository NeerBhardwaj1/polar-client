package dev.polarclient.platform;

import java.util.ArrayList;
import java.util.List;

/** The scoreboard shown at the side of the screen: a title and up to 15 lines (formatted game texts). */
public final class Sidebar {
	public final Object title;
	/** Player/entry names (with team colours), top to bottom. */
	public final List<Object> names = new ArrayList<Object>();
	/** The score next to each name (a formatted text, possibly empty). */
	public final List<Object> scores = new ArrayList<Object>();

	public Sidebar(Object title) {
		this.title = title;
	}

	public void add(Object name, Object score) {
		names.add(name);
		scores.add(score);
	}

	public int size() {
		return names.size();
	}
}
