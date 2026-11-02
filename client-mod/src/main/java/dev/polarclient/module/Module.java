package dev.polarclient.module;

import com.google.gson.JsonObject;
import dev.polarclient.setting.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A toggleable feature with settings, listed in the Polar mod menu. */
public abstract class Module {
	private final String id;
	private final String name;
	private final String description;
	private final Category category;
	private boolean enabled;
	private final List<Setting<?>> settings = new ArrayList<>();

	protected Module(String id, String name, String description, Category category, boolean enabledByDefault) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.enabled = enabledByDefault;
	}

	protected final <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public Category category() {
		return category;
	}

	public List<Setting<?>> settings() {
		return Collections.unmodifiableList(settings);
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) return;
		this.enabled = enabled;
		if (enabled) onEnable();
		else onDisable();
	}

	public void toggle() {
		if (canToggle()) setEnabled(!enabled);
	}

	/** Settings-only modules (like the client settings) are always on and show no toggle. */
	public boolean canToggle() {
		return true;
	}

	/** Called when switched on. Game options may not exist yet at startup, so keep this light. */
	protected void onEnable() {
	}

	/** Called when switched off: undo anything the module changed. */
	protected void onDisable() {
	}

	/** Runs every client tick while the game is running (enabled or not, so modules can clean up). */
	public void tick(Minecraft mc) {
	}

	public JsonObject save() {
		JsonObject o = new JsonObject();
		o.addProperty("enabled", enabled);
		JsonObject s = new JsonObject();
		for (Setting<?> setting : settings) s.add(setting.id(), setting.save());
		o.add("settings", s);
		return o;
	}

	public void load(JsonObject o) {
		if (o.has("enabled")) enabled = o.get("enabled").getAsBoolean();
		if (o.has("settings") && o.get("settings").isJsonObject()) {
			JsonObject s = o.getAsJsonObject("settings");
			for (Setting<?> setting : settings) {
				if (s.has(setting.id())) setting.load(s.get(setting.id()));
			}
		}
	}

	public void resetSettings() {
		for (Setting<?> setting : settings) setting.reset();
	}
}
