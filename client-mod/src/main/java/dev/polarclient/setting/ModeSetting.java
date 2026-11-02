package dev.polarclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

/** One choice out of a fixed list, cycled with arrows in the menu. */
public class ModeSetting extends Setting<String> {
	private final List<String> modes;

	public ModeSetting(String id, String name, String defaultValue, String... modes) {
		super(id, name, defaultValue);
		this.modes = List.of(modes);
	}

	public List<String> modes() {
		return modes;
	}

	public boolean is(String mode) {
		return value.equals(mode);
	}

	public void cycle(int direction) {
		int i = modes.indexOf(value);
		int next = Math.floorMod(i + direction, modes.size());
		set(modes.get(next));
	}

	@Override
	public void set(String v) {
		super.set(modes.contains(v) ? v : modes.getFirst());
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) {
			set(element.getAsString());
		}
	}
}
