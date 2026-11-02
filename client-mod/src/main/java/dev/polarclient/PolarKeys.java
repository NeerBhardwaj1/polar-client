package dev.polarclient;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Polar Client key binds — all rebindable in Options > Controls > Key Binds under "Polar Client". */
public final class PolarKeys {
	public static KeyMapping.Category CATEGORY;
	public static KeyMapping OPEN_MENU;
	public static KeyMapping HUD_EDITOR;
	public static KeyMapping ZOOM;
	public static KeyMapping FREELOOK;

	private PolarKeys() {
	}

	static void register() {
		CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(PolarClient.MOD_ID, "main"));
		OPEN_MENU = key("menu", InputConstants.KEY_RSHIFT);
		HUD_EDITOR = key("hud_editor", InputConstants.UNKNOWN.getValue());
		ZOOM = key("zoom", InputConstants.KEY_C);
		FREELOOK = key("freelook", InputConstants.KEY_LALT);
	}

	private static KeyMapping key(String name, int defaultKey) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.polarclient." + name, defaultKey, CATEGORY));
	}
}
