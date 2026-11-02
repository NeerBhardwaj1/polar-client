package dev.polarclient.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Favourite mods and saved mod profiles, kept in polarclient.json next to the module settings. */
public final class ClientData {
	/** A saved setup: which mods are on, their settings and the HUD layout. */
	public static final class Profile {
		public String name;
		public final JsonObject modules;
		public final long savedAt;

		Profile(String name, JsonObject modules, long savedAt) {
			this.name = name;
			this.modules = modules;
			this.savedAt = savedAt;
		}

		public int enabledCount() {
			int n = 0;
			for (java.util.Map.Entry<String, JsonElement> entry : modules.entrySet()) {
				JsonElement e = entry.getValue();
				if (e.isJsonObject() && e.getAsJsonObject().has("enabled") && e.getAsJsonObject().get("enabled").getAsBoolean()) n++;
			}
			return n;
		}
	}

	public static final int MAX_PROFILES = 24;

	private final Set<String> favorites = new LinkedHashSet<String>();
	private final List<Profile> profiles = new ArrayList<Profile>();
	private String activeProfile;

	public boolean isFavorite(String moduleId) {
		return favorites.contains(moduleId);
	}

	public void toggleFavorite(String moduleId) {
		if (!favorites.remove(moduleId)) favorites.add(moduleId);
	}

	public boolean hasFavorites() {
		return !favorites.isEmpty();
	}

	public List<Profile> profiles() {
		return profiles;
	}

	public String activeProfile() {
		return activeProfile;
	}

	public void setActiveProfile(String name) {
		activeProfile = name;
	}

	public Profile profile(String name) {
		for (Profile p : profiles) if (p.name.equalsIgnoreCase(name)) return p;
		return null;
	}

	/** Adds or replaces the profile with this name. */
	public Profile put(String name, JsonObject modules) {
		Profile old = profile(name);
		Profile p = new Profile(name, modules, System.currentTimeMillis());
		if (old != null) profiles.set(profiles.indexOf(old), p);
		else profiles.add(p);
		return p;
	}

	public void remove(Profile p) {
		profiles.remove(p);
		if (p.name.equals(activeProfile)) activeProfile = null;
	}

	/** "Profile 3", "Imported 2", ... not used yet. */
	public String freeName(String base) {
		for (int i = 1; ; i++) {
			String n = base + " " + i;
			if (profile(n) == null) return n;
		}
	}

	public static String cleanName(String raw) {
		String n = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
		if (n.length() > 24) n = n.substring(0, 24).trim();
		return n;
	}

	public static boolean sameName(String a, String b) {
		return a != null && b != null && a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
	}

	// ------------------------------------------------------------------ json

	void load(JsonObject root) {
		favorites.clear();
		profiles.clear();
		activeProfile = null;
		if (root.has("favorites") && root.get("favorites").isJsonArray()) {
			for (JsonElement e : root.getAsJsonArray("favorites")) favorites.add(e.getAsString());
		}
		if (root.has("profiles") && root.get("profiles").isJsonArray()) {
			for (JsonElement e : root.getAsJsonArray("profiles")) {
				if (!e.isJsonObject()) continue;
				JsonObject o = e.getAsJsonObject();
				if (!o.has("name") || !o.has("modules") || !o.get("modules").isJsonObject()) continue;
				long at = o.has("savedAt") ? o.get("savedAt").getAsLong() : 0;
				if (profiles.size() < MAX_PROFILES) profiles.add(new Profile(o.get("name").getAsString(), o.getAsJsonObject("modules"), at));
			}
		}
		if (root.has("activeProfile") && root.get("activeProfile").isJsonPrimitive()) activeProfile = root.get("activeProfile").getAsString();
	}

	void save(JsonObject root) {
		JsonArray fav = new JsonArray();
		for (String id : favorites) fav.add(new JsonPrimitive(id));
		root.add("favorites", fav);
		JsonArray list = new JsonArray();
		for (Profile p : profiles) {
			JsonObject o = new JsonObject();
			o.addProperty("name", p.name);
			o.addProperty("savedAt", p.savedAt);
			o.add("modules", p.modules);
			list.add(o);
		}
		root.add("profiles", list);
		if (activeProfile != null) root.addProperty("activeProfile", activeProfile);
	}
}
