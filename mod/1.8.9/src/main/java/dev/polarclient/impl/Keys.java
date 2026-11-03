package dev.polarclient.impl;

import dev.polarclient.platform.PolarKey;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.input.Keyboard;

import java.util.EnumMap;
import java.util.Map;

/**
 * Polar Client key binds — all rebindable in Options > Controls under "Polar Client". 1.8.9 has no key bind API, so
 * GameOptionsMixin adds these to the options' key list before options.txt is read.
 */
public final class Keys {
	private static final String CATEGORY = "key.category.polarclient.main";
	private static final Map<PolarKey, KeyBinding> KEYS = new EnumMap<PolarKey, KeyBinding>(PolarKey.class);

	static {
		add(PolarKey.MENU, Keyboard.KEY_RSHIFT);
		add(PolarKey.HUD_EDITOR, Keyboard.KEY_NONE);
		add(PolarKey.ZOOM, Keyboard.KEY_C);
		add(PolarKey.FREELOOK, Keyboard.KEY_LMENU);
		add(PolarKey.COPY_COORDS, Keyboard.KEY_J);
		add(PolarKey.ADD_WAYPOINT, Keyboard.KEY_B);
		add(PolarKey.WAYPOINTS, Keyboard.KEY_NONE);
		add(PolarKey.HITBOXES, Keyboard.KEY_H);
		add(PolarKey.JOIN_INVITE, Keyboard.KEY_Y);
	}

	private Keys() {
	}

	private static void add(PolarKey key, int defaultKey) {
		KEYS.put(key, new KeyBinding(key.translationKey(), defaultKey, CATEGORY));
	}

	public static KeyBinding get(PolarKey key) {
		return KEYS.get(key);
	}

	public static KeyBinding[] all() {
		return KEYS.values().toArray(new KeyBinding[0]);
	}
}
