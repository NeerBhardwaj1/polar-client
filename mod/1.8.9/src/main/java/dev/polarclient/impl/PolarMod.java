package dev.polarclient.impl;

import dev.polarclient.Polar;
import net.fabricmc.api.ClientModInitializer;

/**
 * Minecraft 1.8.9 (Legacy Fabric) entrypoint: hands the shared core a {@link McPlatform}. 1.8.9 has no Fabric events,
 * so ticks, the HUD, chat, hits and key binds all come from mixins.
 */
public class PolarMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Polar.init(new McPlatform());
	}
}
