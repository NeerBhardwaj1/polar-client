package dev.polarclient.modules.hud;

import dev.polarclient.module.Category;
import dev.polarclient.module.TextHudModule;

import java.util.List;
import java.util.Locale;

/** Exact health and absorption. */
public class HealthModule extends TextHudModule {
	public HealthModule() {
		super("health", "Health HUD", "Show your exact health.", Category.HUD, false, 360, 212);
	}

	@Override
	protected List<String> lines(boolean preview) {
		boolean live = !preview || p().hasPlayer();
		float hp = live ? p().health() : 17.5f, max = live ? p().maxHealth() : 20f, abs = live ? p().absorption() : 4f;
		String s = String.format(Locale.ROOT, "Health: %.1f / %.0f", hp, max);
		if (abs > 0) s += String.format(Locale.ROOT, "  +%.0f", abs);
		return one(s);
	}

	@Override
	public boolean hasContent() {
		return p().hasPlayer() && !p().creativeOrSpectator();
	}
}
