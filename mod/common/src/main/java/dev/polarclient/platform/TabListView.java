package dev.polarclient.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** What the player list (Tab) shows: header, players and footer. */
public final class TabListView {
	public static final class Entry {
		public final Object name;
		public final String profileName;
		public final UUID id;
		public final int ping;
		public final boolean spectator;

		public Entry(Object name, String profileName, UUID id, int ping, boolean spectator) {
			this.name = name;
			this.profileName = profileName;
			this.id = id;
			this.ping = ping;
			this.spectator = spectator;
		}
	}

	/** Formatted texts, or null when the server sends none. */
	public final Object header, footer;
	public final List<Entry> entries = new ArrayList<Entry>();

	public TabListView(Object header, Object footer) {
		this.header = header;
		this.footer = footer;
	}
}
