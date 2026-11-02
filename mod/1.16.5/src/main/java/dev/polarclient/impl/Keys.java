package dev.polarclient.impl;

import dev.polarclient.platform.PolarKey;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.Map;

/** Polar Client key binds — all rebindable in Options > Controls under "Polar Client". */
public final class Keys {
	private static final String CATEGORY = "key.category.polarclient.main";
	private static final Map<PolarKey, KeyMapping> KEYS = new EnumMap<PolarKey, KeyMapping>(PolarKey.class);

	private Keys() {
	}

	static void register() {
		add(PolarKey.MENU, GLFW.GLFW_KEY_RIGHT_SHIFT);
		add(PolarKey.HUD_EDITOR, GLFW.GLFW_KEY_UNKNOWN);
		add(PolarKey.ZOOM, GLFW.GLFW_KEY_C);
		add(PolarKey.FREELOOK, GLFW.GLFW_KEY_LEFT_ALT);
		add(PolarKey.COPY_COORDS, GLFW.GLFW_KEY_J);
		add(PolarKey.ADD_WAYPOINT, GLFW.GLFW_KEY_B);
		add(PolarKey.WAYPOINTS, GLFW.GLFW_KEY_UNKNOWN);
		add(PolarKey.HITBOXES, GLFW.GLFW_KEY_H);
	}

	private static void add(PolarKey key, int defaultKey) {
		KEYS.put(key, KeyBindingHelper.registerKeyBinding(new KeyMapping(key.translationKey(), defaultKey, CATEGORY)));
	}

	public static KeyMapping get(PolarKey key) {
		return KEYS.get(key);
	}
}
