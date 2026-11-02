package dev.polarclient.setting;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;

/** A single configurable value on a module, shown in the mod menu's settings page. */
public abstract class Setting<T> {
	private final String id;
	private final String name;
	private final T defaultValue;
	protected T value;
	private BooleanSupplier visibility = () -> true;

	protected Setting(String id, String name, T defaultValue) {
		this.id = id;
		this.name = name;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public T get() {
		return value;
	}

	public void set(T value) {
		this.value = value;
	}

	public void reset() {
		set(defaultValue);
	}

	public T defaultValue() {
		return defaultValue;
	}

	/** Hides this setting in the menu unless the condition holds (e.g. only show "Color" when "Background" is on). */
	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S visibleWhen(BooleanSupplier condition) {
		this.visibility = condition;
		return (S) this;
	}

	public boolean isVisible() {
		return visibility.getAsBoolean();
	}

	public abstract JsonElement save();

	public abstract void load(JsonElement element);
}
