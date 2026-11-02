package dev.polarclient;

import dev.polarclient.config.ConfigManager;
import dev.polarclient.gui.HudEditorScreen;
import dev.polarclient.gui.PolarMenuScreen;
import dev.polarclient.hud.HudRenderer;
import dev.polarclient.module.Module;
import dev.polarclient.module.ModuleManager;
import dev.polarclient.util.CombatTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PolarClient implements ClientModInitializer {
	public static final String MOD_ID = "polarclient";
	public static final Logger LOGGER = LoggerFactory.getLogger("Polar Client");

	private static ModuleManager modules;
	private static ConfigManager config;
	private static String version = "dev";

	@Override
	public void onInitializeClient() {
		version = FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");

		modules = new ModuleManager();
		config = new ConfigManager(modules);
		config.load();

		PolarKeys.register();
		HudRenderer.register();

		ClientTickEvents.END_CLIENT_TICK.register(PolarClient::tick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> config.save());
		AttackEntityCallback.EVENT.register(CombatTracker::onAttack);
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> modules.autoGG.onGameMessage(message, overlay));

		LOGGER.info("Polar Client {} loaded with {} modules", version, modules.all().size());
	}

	private static void tick(Minecraft mc) {
		while (PolarKeys.OPEN_MENU.consumeClick()) {
			if (mc.gui.screen() == null) mc.gui.setScreen(new PolarMenuScreen(null));
		}
		while (PolarKeys.HUD_EDITOR.consumeClick()) {
			if (mc.gui.screen() == null) mc.gui.setScreen(new HudEditorScreen(null));
		}
		CombatTracker.tick(mc);
		for (Module m : modules.all()) {
			try {
				m.tick(mc);
			} catch (RuntimeException e) {
				LOGGER.error("Module {} failed during tick; disabling it", m.id(), e);
				m.setEnabled(false);
			}
		}
	}

	public static ModuleManager modules() {
		return modules;
	}

	public static void saveConfig() {
		if (config != null) config.save();
	}

	public static String version() {
		return version;
	}
}
