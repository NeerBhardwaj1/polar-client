package dev.polarclient.modules.hud;

import dev.polarclient.module.TextHudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.List;

public class PingModule extends TextHudModule {
	public PingModule() {
		super("ping", "Ping", "Your latency to the server in milliseconds.", true, 4, 60);
	}

	@Override
	public boolean hasContent() {
		return Minecraft.getInstance().getSingleplayerServer() == null;
	}

	@Override
	protected List<String> lines(boolean preview) {
		Minecraft mc = Minecraft.getInstance();
		ClientPacketListener connection = mc.getConnection();
		if (connection != null && mc.player != null) {
			PlayerInfo info = connection.getPlayerInfo(mc.player.getUUID());
			if (info != null) return List.of(info.getLatency() + " ms");
		}
		return List.of(preview ? "42 ms" : "? ms");
	}
}
