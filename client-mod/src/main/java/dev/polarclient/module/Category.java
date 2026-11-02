package dev.polarclient.module;

public enum Category {
	HUD("HUD"),
	COMBAT("Combat"),
	VISUAL("Visual"),
	UTILITY("Utility");

	public final String label;

	Category(String label) {
		this.label = label;
	}
}
