package dev.polarclient.platform;

import java.util.List;

/**
 * The game's saved server list (servers.dat), its server pinger and LAN discovery, for Polar's server list screen.
 * One instance per opened screen; {@link #close()} when the screen goes away.
 */
public interface ServerBackend {
	enum Status { INITIAL, PINGING, SUCCESSFUL, INCOMPATIBLE, UNREACHABLE }

	/** One saved server. */
	interface Server {
		String name();

		String address();

		Status status();

		long ping();

		boolean hasPlayerCount();

		int online();

		int max();

		/** Player names from the ping, for the hover tooltip. */
		List<String> playerSample();

		/** Server's version text (shown when incompatible). */
		String versionName();

		/** The MOTD as the version's own text component (drawn with {@link dev.polarclient.gfx.Gfx#richText}). */
		Object motd();

		/** Favicon texture handle for {@link dev.polarclient.gfx.Gfx#blitHandle} (the game's "unknown server" icon when there is none). */
		Object icon();
	}

	/** A world opened to LAN. */
	interface Lan {
		String motd();

		String address();
	}

	/** Re-reads servers.dat (the list returns here after Add/Edit/Delete). */
	void load();

	List<Server> servers();

	/** Starts pinging the server if it hasn't been yet; also refreshes its favicon. */
	void ping(Server server);

	void swap(int i, int j);

	void tick();

	List<Lan> lan();

	void close();

	// vanilla dialogs; they come back to the screen given to Platform#createServerBackend
	void openAdd();

	void openEdit(Server server);

	void openDelete(Server server);

	/** "Direct connect": joins straight away and saves the server if it's new. */
	void openDirect();

	void join(Server server);

	void joinLan(Lan lan);
}
