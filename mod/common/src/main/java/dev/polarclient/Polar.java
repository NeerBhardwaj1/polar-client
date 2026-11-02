package dev.polarclient;

import dev.polarclient.config.ConfigManager;
import dev.polarclient.gui.HudEditorView;
import dev.polarclient.gui.PolarMenuView;
import dev.polarclient.module.Module;
import dev.polarclient.module.ModuleManager;
import dev.polarclient.platform.Platform;
import dev.polarclient.platform.PolarKey;
import dev.polarclient.util.CombatTracker;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * Polar Client's shared core: the module list, config and per-tick logic. Each Minecraft version's entrypoint calls
 * {@link #init} with its {@link Platform}, then {@link #tick} at the end of every client tick.
 */
public final class Polar {
	public static final String MOD_ID = "polarclient";

	private static ModuleManager modules;
	private static ConfigManager config;
	private static String version = "dev";

	private Polar() {
	}

	public static void init(Platform platform) {
		Platform.set(platform);
		ModContainer mod = FabricLoader.getInstance().getModContainer(MOD_ID).orElse(null);
		if (mod != null) version = mod.getMetadata().getVersion().getFriendlyString();
		modules = new ModuleManager();
		config = new ConfigManager(modules);
		config.load();
		Log.info("Polar Client " + version + " for Minecraft " + platform.minecraftVersion() + " loaded with " + modules.all().size() + " modules");
	}

	/** End of every client tick. */
	public static void tick() {
		Platform p = Platform.get();
		if (p == null || modules == null) return;
		while (p.consumePolarKey(PolarKey.MENU)) {
			if (!p.screenOpen()) p.open(new PolarMenuView(null));
		}
		while (p.consumePolarKey(PolarKey.HUD_EDITOR)) {
			if (!p.screenOpen()) p.open(new HudEditorView(null));
		}
		CombatTracker.tick();
		for (Module m : modules.all()) {
			try {
				m.tick();
			} catch (RuntimeException e) {
				Log.error("Module " + m.id() + " failed during tick; disabling it", e);
				m.setEnabled(false);
			}
		}
	}

	public static ModuleManager modules() {
		return modules;
	}

	public static ConfigManager config() {
		return config;
	}

	/** System/server messages (each version calls this from its chat hook). {@code overlay} = action bar. */
	public static void onGameMessage(String text, boolean overlay) {
		if (modules == null || text == null) return;
		for (Module m : modules.all()) {
			try {
				m.onGameMessage(text, overlay);
			} catch (RuntimeException e) {
				Log.error("Module " + m.id() + " failed handling a message", e);
			}
		}
	}

	/** Chat from other players (each version calls this from its chat hook). Text without formatting codes. */
	public static void onChatMessage(String text) {
		if (modules == null || text == null) return;
		for (Module m : modules.all()) {
			try {
				m.onChatMessage(text);
			} catch (RuntimeException e) {
				Log.error("Module " + m.id() + " failed handling chat", e);
			}
		}
	}

	public static void saveConfig() {
		if (config != null) config.save();
	}

	public static String version() {
		return version;
	}
}
