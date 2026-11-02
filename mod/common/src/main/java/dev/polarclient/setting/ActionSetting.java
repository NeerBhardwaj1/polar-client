package dev.polarclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** A button in a module's settings ("Manage waypoints"). Not saved. */
public final class ActionSetting extends Setting<String> {
	private final String button;
	private final Runnable action;

	/** {@code description} is shown under the name, {@code button} on the button. */
	public ActionSetting(String id, String name, String description, String button, Runnable action) {
		super(id, name, description);
		this.button = button;
		this.action = action;
	}

	public String button() {
		return button;
	}

	public void run() {
		action.run();
	}

	@Override
	public void set(String value) {
		// fixed text
	}

	@Override
	public JsonElement save() {
		return JsonNull.INSTANCE;
	}

	@Override
	public void load(JsonElement element) {
	}
}
