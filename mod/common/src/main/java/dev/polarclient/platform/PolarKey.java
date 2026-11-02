package dev.polarclient.platform;

/** Polar's own key binds, listed under "Polar Client" in Controls. */
public enum PolarKey {
	MENU("menu"), HUD_EDITOR("hud_editor"), ZOOM("zoom"), FREELOOK("freelook"), COPY_COORDS("copy_coordinates"),
	ADD_WAYPOINT("add_waypoint"), WAYPOINTS("waypoints"), HITBOXES("hitboxes");

	public final String id;

	PolarKey(String id) {
		this.id = id;
	}

	/** Translation key, e.g. key.polarclient.menu. */
	public String translationKey() {
		return "key.polarclient." + id;
	}
}
