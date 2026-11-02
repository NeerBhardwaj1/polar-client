package dev.polarclient.modules.hud;

import dev.polarclient.module.TextHudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.List;

public class ServerAddressModule extends TextHudModule {
	public ServerAddressModule() {
		super("server_address", "Server Address", "The server you're playing on.", false, 200, 4);
	}

	@Override
	protected List<String> lines(boolean preview) {
		Minecraft mc = Minecraft.getInstance();
		ServerData server = mc.getCurrentServer();
		if (server != null) return List.of(server.ip);
		if (mc.getSingleplayerServer() != null) return List.of("Singleplayer");
		return List.of(preview ? "play.example.net" : "Offline");
	}
}
