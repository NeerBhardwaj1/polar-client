package dev.polarclient.gui.ui;

import dev.polarclient.module.Module;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ColorSetting;
import dev.polarclient.setting.ModeSetting;
import dev.polarclient.setting.NumberSetting;
import dev.polarclient.setting.Setting;
import dev.polarclient.setting.TextSetting;

import java.util.Map;

/** Which Material icon represents each module and setting. */
public final class Icons {
	private Icons() {
	}

	private static final Map<String, String> MODULES = Map.ofEntries(
		Map.entry("client", Icon.SETTINGS),
		Map.entry("zoom", Icon.ZOOM),
		Map.entry("freelook", Icon.EYE),
		Map.entry("fullbright", Icon.SUN),
		Map.entry("custom_crosshair", Icon.TARGET),
		Map.entry("hud_tweaks", Icon.DASHBOARD),
		Map.entry("auto_gg", Icon.CELEBRATION),
		Map.entry("low_health_warning", Icon.HEART_BROKEN),
		Map.entry("no_hurt_cam", Icon.FOCUS),
		Map.entry("watermark", Icon.SNOWFLAKE),
		Map.entry("fps", Icon.SPEED),
		Map.entry("cps", Icon.CLICK),
		Map.entry("ping", Icon.SIGNAL),
		Map.entry("keystrokes", Icon.KEYBOARD),
		Map.entry("armor_status", Icon.SHIELD),
		Map.entry("potion_effects", Icon.POTION),
		Map.entry("toggle_sprint", Icon.SPRINT),
		Map.entry("coordinates", Icon.LOCATION),
		Map.entry("compass", Icon.COMPASS),
		Map.entry("clock", Icon.CLOCK),
		Map.entry("server_address", Icon.SERVER),
		Map.entry("speed", Icon.RUN),
		Map.entry("reach_display", Icon.RULER),
		Map.entry("combo_counter", Icon.FIRE),
		Map.entry("item_counter", Icon.INVENTORY),
		Map.entry("session_time", Icon.HOURGLASS),
		Map.entry("memory", Icon.MEMORY)
	);

	private static final Map<String, String> SETTINGS = Map.ofEntries(
		Map.entry("scale", Icon.ZOOM),
		Map.entry("factor", Icon.ZOOM),
		Map.entry("background", Icon.LAYERS),
		Map.entry("menu_blur", Icon.LAYERS),
		Map.entry("shadow", Icon.LAYERS),
		Map.entry("accent", Icon.PALETTE),
		Map.entry("amoled", Icon.DARK_MODE),
		Map.entry("custom_main_menu", Icon.HOME),
		Map.entry("snow", Icon.SNOWFLAKE),
		Map.entry("window_title", Icon.APPS),
		Map.entry("hide_hud_in_debug", Icon.DASHBOARD),
		Map.entry("delay", Icon.CLOCK),
		Map.entry("threshold", Icon.HEART_BROKEN),
		Map.entry("scroll", Icon.CLICK),
		Map.entry("cinematic", Icon.EYE),
		Map.entry("activation", Icon.KEYBOARD),
		Map.entry("third_person", Icon.PERSON),
		Map.entry("item", Icon.INVENTORY),
		Map.entry("message", Icon.EDIT),
		Map.entry("smooth", Icon.BOLT),
		Map.entry("fade", Icon.BOLT),
		Map.entry("mode", Icon.DASHBOARD),
		Map.entry("style", Icon.TARGET),
		Map.entry("layout", Icon.DASHBOARD),
		Map.entry("direction", Icon.COMPASS),
		Map.entry("biome", Icon.LOCATION),
		Map.entry("twelve_hour", Icon.CLOCK)
	);

	public static String of(Module m) {
		return MODULES.getOrDefault(m.id(), Icon.MODS);
	}

	public static String of(Setting<?> s) {
		String icon = SETTINGS.get(s.id());
		if (icon != null) return icon;
		if (s.id().contains("color")) return Icon.PALETTE;
		return switch (s) {
			case BooleanSetting b -> Icon.TUNE;
			case NumberSetting n -> Icon.TUNE;
			case ModeSetting m -> Icon.DASHBOARD;
			case ColorSetting c -> Icon.PALETTE;
			case TextSetting t -> Icon.EDIT;
			default -> Icon.TUNE;
		};
	}
}
