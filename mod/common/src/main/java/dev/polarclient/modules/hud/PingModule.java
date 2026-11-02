package dev.polarclient.modules.hud;

import dev.polarclient.module.TextHudModule;

import java.util.List;

public class PingModule extends TextHudModule {
	public PingModule() {
		super("ping", "Ping", "Show your server ping.", true, 4, 60);
	}

	@Override
	public boolean hasContent() {
		return !p().isSingleplayer();
	}

	@Override
	protected List<String> lines(boolean preview) {
		int ping = p().hasPlayer() ? p().ping() : -1;
		if (ping >= 0) return one(ping + " ms");
		return one(preview ? "42 ms" : "? ms");
	}
}
