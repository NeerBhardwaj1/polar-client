package dev.polarclient.modules.social;

import dev.polarclient.account.LauncherBridge;
import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.BooleanSetting;

/**
 * Feeds the launcher's Discord status ("Minecraft 1.21.11 · On play.example.net"). The launcher talks to Discord; this
 * only says whether you're in the menus, in singleplayer or on a server. Off = Discord shows "Playing" with no details.
 */
public class DiscordStatusModule extends Module {
	private static final long MIN_GAP_MS = 1500;

	private final BooleanSetting showServer = add(new BooleanSetting("show_server", "Show server address", true));

	private String sent;
	private long sentAt;

	public DiscordStatusModule() {
		super("discord_status", "Discord Status", "Show where you're playing on Discord.", Category.SOCIAL, true);
	}

	@Override
	public void tick() {
		if (!LauncherBridge.available()) return;
		String state, server = null;
		if (!isEnabled()) state = "hidden";
		else if (!p().inWorld()) state = "menu";
		else if (p().isSingleplayer()) state = "singleplayer";
		else {
			state = "server";
			// Minecraft's own "Hide server addresses" (streamer) option wins too
			if (showServer.on() && !p().hideServerAddresses()) server = p().serverAddress();
		}
		String key = server == null ? state : state + ' ' + server;
		long now = System.currentTimeMillis();
		// joining a server passes through a few screens; at most one update every 1.5 s, the last one always lands
		if (key.equals(sent) || now - sentAt < MIN_GAP_MS) return;
		sent = key;
		sentAt = now;
		LauncherBridge.presence(state, server);
	}
}
