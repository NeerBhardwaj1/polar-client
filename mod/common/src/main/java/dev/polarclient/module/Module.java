package dev.polarclient.module;

import com.google.gson.JsonObject;
import dev.polarclient.platform.Platform;
import dev.polarclient.setting.Setting;

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
	private final List<Setting<?>> settings = new ArrayList<Setting<?>>();

	protected Module(String id, String name, String description, Category category, boolean enabledByDefault) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.enabled = enabledByDefault;
	}

	protected static Platform p() {
		return Platform.get();
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

	/** A server/system message arrived ({@code overlay} = action bar). Text without formatting codes. */
	public void onGameMessage(String text, boolean overlay) {
	}

	/** Another player's chat message arrived. Text without formatting codes. */
	public void onChatMessage(String text) {
	}

	/** Drawn with the HUD every frame while the module is on (for things that aren't movable HUD elements). */
	public void renderOverlay(dev.polarclient.gfx.Gfx g) {
	}

	/** Called when switched on. Game options may not exist yet at startup, so keep this light. */
	protected void onEnable() {
	}

	/** Called when switched off: undo anything the module changed. */
	protected void onDisable() {
	}

	/** Runs every client tick while the game is running (enabled or not, so modules can clean up). */
	public void tick() {
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

	/**
	 * Applies a saved state (from a profile or an imported config) while the game runs: settings first, then the
	 * on/off state through {@link #setEnabled}, so modules can react to being switched.
	 */
	public void apply(JsonObject o) {
		boolean was = enabled;
		load(o);
		boolean now = enabled;
		enabled = was;
		setEnabled(now);
	}

	/** Null when the module works on this Minecraft version, otherwise a short reason it doesn't. */
	public String unavailableReason() {
		return null;
	}

	public void resetSettings() {
		for (Setting<?> setting : settings) setting.reset();
	}
}
