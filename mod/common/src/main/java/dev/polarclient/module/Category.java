package dev.polarclient.module;

import dev.polarclient.gui.ui.Icon;

/** The mod menu's categories (its filter chips), in menu order. */
public enum Category {
	HUD("HUD", Icon.DASHBOARD),
	VISUAL("Visual", Icon.EYE),
	COMBAT("Combat", Icon.SWORDS),
	UTILITY("Utility", Icon.BUILD),
	WORLD("World", Icon.PUBLIC),
	SOCIAL("Social", Icon.CHAT),
	PERFORMANCE("Performance", Icon.SPEED),
	ACCESSIBILITY("Accessibility", Icon.MOUSE),
	CUSTOMIZATION("Customization", Icon.PALETTE);

	public final String label;
	public final String icon;

	Category(String label, String icon) {
		this.label = label;
		this.icon = icon;
	}
}
