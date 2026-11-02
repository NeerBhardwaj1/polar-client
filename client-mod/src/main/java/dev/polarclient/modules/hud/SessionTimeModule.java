package dev.polarclient.modules.hud;

import dev.polarclient.module.TextHudModule;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

public class SessionTimeModule extends TextHudModule {
	private long joinedAt = -1;

	public SessionTimeModule() {
		super("session_time", "Session Time", "How long you've been in this world or server.", false, 4, 190);
	}

	@Override
	public void tick(Minecraft mc) {
		if (mc.level == null) joinedAt = -1;
		else if (joinedAt < 0) joinedAt = System.currentTimeMillis();
	}

	@Override
	protected List<String> lines(boolean preview) {
		long secs = joinedAt < 0 ? 754 : (System.currentTimeMillis() - joinedAt) / 1000;
		long h = secs / 3600, m = (secs / 60) % 60, s = secs % 60;
		return List.of(h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%02d:%02d", m, s));
	}
}
