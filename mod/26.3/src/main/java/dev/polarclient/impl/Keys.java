package dev.polarclient.impl;

import com.mojang.blaze3d.platform.InputConstants;
import dev.polarclient.Polar;
import dev.polarclient.platform.PolarKey;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.Map;

/** Polar Client key binds — all rebindable in Options > Controls > Key Binds under "Polar Client". */
public final class Keys {
	private static final Map<PolarKey, KeyMapping> KEYS = new EnumMap<>(PolarKey.class);

	private Keys() {
	}

	static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Polar.MOD_ID, "main"));
		add(PolarKey.MENU, InputConstants.KEY_RSHIFT, category);
		add(PolarKey.HUD_EDITOR, InputConstants.UNKNOWN.getValue(), category);
		add(PolarKey.ZOOM, InputConstants.KEY_C, category);
		add(PolarKey.FREELOOK, InputConstants.KEY_LALT, category);
		add(PolarKey.COPY_COORDS, InputConstants.KEY_J, category);
		add(PolarKey.ADD_WAYPOINT, InputConstants.KEY_B, category);
		add(PolarKey.WAYPOINTS, InputConstants.UNKNOWN.getValue(), category);
		add(PolarKey.HITBOXES, InputConstants.KEY_H, category);
	}

	private static void add(PolarKey key, int defaultKey, KeyMapping.Category category) {
		KEYS.put(key, KeyMappingHelper.registerKeyMapping(new KeyMapping(key.translationKey(), defaultKey, category)));
	}

	public static KeyMapping get(PolarKey key) {
		return KEYS.get(key);
	}
}
