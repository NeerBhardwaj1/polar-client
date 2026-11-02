package dev.polarclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Free text, edited with a text box in the menu. */
public class TextSetting extends Setting<String> {
	private final int maxLength;

	public TextSetting(String id, String name, String defaultValue, int maxLength) {
		super(id, name, defaultValue);
		this.maxLength = maxLength;
	}

	public int maxLength() {
		return maxLength;
	}

	@Override
	public void set(String v) {
		String s = v == null ? "" : v;
		super.set(s.length() > maxLength ? s.substring(0, maxLength) : s);
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
