package dev.polarclient.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.polarclient.Log;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Waypoints for every server and singleplayer world, kept in config/polarclient-waypoints.json. */
public final class WaypointStore {
	public static final int MAX_PER_WORLD = 200;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Map<String, List<Waypoint>> worlds;

	private WaypointStore() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("polarclient-waypoints.json");
	}

	private static Map<String, List<Waypoint>> all() {
		if (worlds == null) load();
		return worlds;
	}

	/** The waypoints of a world (live list; call {@link #save} after changing it). Empty when worldId is null. */
	public static List<Waypoint> of(String worldId) {
		if (worldId == null) return new ArrayList<Waypoint>();
		List<Waypoint> list = all().get(worldId);
		if (list == null) {
			list = new ArrayList<Waypoint>();
			all().put(worldId, list);
		}
		return list;
	}

	private static void load() {
		worlds = new LinkedHashMap<String, List<Waypoint>>();
		try {
			Path f = file();
			if (!Files.exists(f)) return;
			JsonElement root = new JsonParser().parse(new String(Files.readAllBytes(f), StandardCharsets.UTF_8));
			if (!root.isJsonObject()) return;
			for (Map.Entry<String, JsonElement> world : root.getAsJsonObject().entrySet()) {
				if (!world.getValue().isJsonArray()) continue;
				List<Waypoint> list = new ArrayList<Waypoint>();
				for (JsonElement e : world.getValue().getAsJsonArray()) {
					if (!e.isJsonObject()) continue;
					JsonObject o = e.getAsJsonObject();
					try {
						Waypoint w = new Waypoint(o.get("name").getAsString(), o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(),
							o.has("dim") ? o.get("dim").getAsString() : "minecraft:overworld", o.has("color") ? o.get("color").getAsInt() : 0xFF8CC8FF);
						if (o.has("visible")) w.visible = o.get("visible").getAsBoolean();
						list.add(w);
					} catch (RuntimeException bad) {
						// skip a broken entry
					}
				}
				worlds.put(world.getKey(), list);
			}
		} catch (Exception e) {
			Log.error("Couldn't read waypoints", e);
		}
	}

	public static void save() {
		if (worlds == null) return;
		JsonObject root = new JsonObject();
		for (Map.Entry<String, List<Waypoint>> world : worlds.entrySet()) {
			if (world.getValue().isEmpty()) continue;
			JsonArray arr = new JsonArray();
			for (Waypoint w : world.getValue()) {
				JsonObject o = new JsonObject();
				o.addProperty("name", w.name);
				o.addProperty("x", w.x);
				o.addProperty("y", w.y);
				o.addProperty("z", w.z);
				o.addProperty("dim", w.dim);
				o.addProperty("color", w.color);
				o.addProperty("visible", w.visible);
				arr.add(o);
			}
			root.add(world.getKey(), arr);
		}
		try {
			Path f = file();
			Files.createDirectories(f.getParent());
			Path tmp = f.resolveSibling("polarclient-waypoints.json.tmp");
			Files.write(tmp, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
			Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			Log.error("Couldn't save waypoints", e);
		}
	}
}
