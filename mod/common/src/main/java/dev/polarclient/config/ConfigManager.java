package dev.polarclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.polarclient.Log;
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
	private final ClientData data = new ClientData();

	public ConfigManager(ModuleManager modules) {
		this.modules = modules;
		this.file = FabricLoader.getInstance().getConfigDir().resolve("polarclient.json");
	}

	public void load() {
		if (!Files.exists(file)) return;
		try {
			@SuppressWarnings("deprecation")
			JsonElement root = new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
			if (!root.isJsonObject()) return;
			try {
				data.load(root.getAsJsonObject());
			} catch (RuntimeException ex) {
				Log.warn("Couldn't read favourites and profiles; starting without them", ex);
			}
			JsonObject mods = root.getAsJsonObject().getAsJsonObject("modules");
			if (mods == null) return;
			for (Module m : modules.all()) {
				JsonElement e = mods.get(m.id());
				if (e != null && e.isJsonObject()) {
					try {
						m.load(e.getAsJsonObject());
					} catch (RuntimeException ex) {
						Log.warn("Couldn't read settings for " + m.id() + "; using defaults", ex);
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			Log.error("Couldn't read " + file + "; starting with defaults", e);
		}
	}

	public ClientData data() {
		return data;
	}

	/** Every module's state, settings and HUD position (what a profile stores). */
	public JsonObject snapshot() {
		JsonObject mods = new JsonObject();
		for (Module m : modules.all()) {
			if (m == modules.client) continue;
			mods.add(m.id(), m.save());
		}
		return mods;
	}

	/** Puts every module in a profile's state. Modules the profile doesn't mention are left as they are. */
	public void apply(JsonObject mods) {
		for (Module m : modules.all()) {
			if (m == modules.client) continue;
			JsonElement e = mods.get(m.id());
			if (e == null || !e.isJsonObject()) continue;
			try {
				m.apply(e.getAsJsonObject());
			} catch (RuntimeException ex) {
				Log.warn("Couldn't apply profile settings for " + m.id(), ex);
			}
		}
	}

	/** The current setup as text for the clipboard (Profiles > Export). */
	public String export(String name) {
		JsonObject o = new JsonObject();
		o.addProperty("polarProfile", 1);
		o.addProperty("name", name);
		o.add("modules", snapshot());
		return GSON.toJson(o);
	}

	/** Reads text made by {@link #export}; returns the modules object, or null if it isn't a Polar profile. */
	public static JsonObject parseExport(String text) {
		if (text == null) return null;
		try {
			@SuppressWarnings("deprecation")
			JsonElement e = new JsonParser().parse(text.trim());
			if (!e.isJsonObject()) return null;
			JsonObject o = e.getAsJsonObject();
			if (!o.has("modules") || !o.get("modules").isJsonObject()) return null;
			return o.getAsJsonObject("modules");
		} catch (RuntimeException ex) {
			return null;
		}
	}

	public static String exportName(String text) {
		try {
			@SuppressWarnings("deprecation")
			JsonObject o = new JsonParser().parse(text.trim()).getAsJsonObject();
			return o.has("name") ? o.get("name").getAsString() : null;
		} catch (RuntimeException ex) {
			return null;
		}
	}

	public void save() {
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		JsonObject mods = snapshot();
		mods.add(modules.client.id(), modules.client.save());
		root.add("modules", mods);
		data.save(root);
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("polarclient.json.tmp");
			Files.write(tmp, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Log.error("Couldn't save " + file, e);
		}
	}
}
