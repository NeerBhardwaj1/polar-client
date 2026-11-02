package dev.polarclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.polarclient.PolarClient;
import dev.polarclient.module.Module;
import dev.polarclient.module.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Saves module states, settings and HUD positions to config/polarclient.json. */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int VERSION = 1;

	private final ModuleManager modules;
	private final Path file;

	public ConfigManager(ModuleManager modules) {
		this.modules = modules;
		this.file = FabricLoader.getInstance().getConfigDir().resolve("polarclient.json");
	}

	public void load() {
		if (!Files.exists(file)) return;
		try {
			JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (!root.isJsonObject()) return;
			JsonObject mods = root.getAsJsonObject().getAsJsonObject("modules");
			if (mods == null) return;
			for (Module m : modules.all()) {
				JsonElement e = mods.get(m.id());
				if (e != null && e.isJsonObject()) {
					try {
						m.load(e.getAsJsonObject());
					} catch (RuntimeException ex) {
						PolarClient.LOGGER.warn("Couldn't read settings for {}; using defaults", m.id(), ex);
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			PolarClient.LOGGER.error("Couldn't read {}; starting with defaults", file, e);
		}
	}

	public void save() {
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		JsonObject mods = new JsonObject();
		for (Module m : modules.all()) mods.add(m.id(), m.save());
		root.add("modules", mods);
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("polarclient.json.tmp");
			Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			PolarClient.LOGGER.error("Couldn't save {}", file, e);
		}
	}
}
