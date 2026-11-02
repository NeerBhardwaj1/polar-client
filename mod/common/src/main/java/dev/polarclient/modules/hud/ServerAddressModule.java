package dev.polarclient.modules.hud;

import dev.polarclient.module.TextHudModule;

import java.util.List;

public class ServerAddressModule extends TextHudModule {
	public ServerAddressModule() {
		super("server_address", "Server Address", "Show the current server.", false, 200, 4);
	}

	@Override
	protected List<String> lines(boolean preview) {
		String server = p().serverAddress();
		if (server != null) return one(server);
		if (p().isSingleplayer()) return one("Singleplayer");
		return one(preview ? "play.example.net" : "Offline");
	}
}
