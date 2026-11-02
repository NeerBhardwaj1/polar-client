package dev.polarclient.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A number shown as a slider. Values are snapped to {@code step} and clamped to [min, max]. */
public class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;
	private final String suffix;

	public NumberSetting(String id, String name, double defaultValue, double min, double max, double step, String suffix) {
		super(id, name, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
		this.suffix = suffix;
	}

	public NumberSetting(String id, String name, double defaultValue, double min, double max, double step) {
		this(id, name, defaultValue, min, max, step, "");
	}

	@Override
	public void set(Double v) {
		double snapped = Math.round(v / step) * step;
		super.set(Math.max(min, Math.min(max, snapped)));
	}

	public double min() {
		return min;
	}

	public double max() {
		return max;
	}

	public float getFloat() {
		return value.floatValue();
	}

	public int getInt() {
		return (int) Math.round(value);
	}

	/** 0..1 position of the current value along the slider. */
	public double fraction() {
		return (value - min) / (max - min);
	}

	public void setFraction(double f) {
		set(min + Math.max(0, Math.min(1, f)) * (max - min));
	}

	public String display() {
		boolean whole = step >= 1 && Math.abs(step - Math.rint(step)) < 1e-9;
		String number = whole ? String.valueOf(getInt()) : String.format(java.util.Locale.ROOT, step >= 0.1 ? "%.1f" : "%.2f", value);
		return number + suffix;
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(JsonElement element) {
		if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
			set(element.getAsDouble());
		}
	}
}
