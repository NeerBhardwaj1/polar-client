package dev.polarclient.modules.hud;

import dev.polarclient.module.TextHudModule;
import dev.polarclient.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CoordinatesModule extends TextHudModule {
	private final BooleanSetting showDirection = add(new BooleanSetting("direction", "Show facing", true));
	private final BooleanSetting showBiome = add(new BooleanSetting("biome", "Show biome", true));
	private final BooleanSetting decimals = add(new BooleanSetting("decimals", "Decimals", false));

	public CoordinatesModule() {
		super("coordinates", "Coordinates", "XYZ position, facing direction and biome.", false, 4, 160);
	}

	@Override
	protected List<String> lines(boolean preview) {
		Player p = Minecraft.getInstance().player;
		List<String> out = new ArrayList<>();
		if (p == null) {
			out.add("X: 128");
			out.add("Y: 64");
			out.add("Z: -256");
			if (showDirection.on()) out.add("Facing: North (-Z)");
			if (showBiome.on()) out.add("Biome: Plains");
			return out;
		}
		String fmt = decimals.on() ? "%.1f" : "%.0f";
		out.add("X: " + String.format(Locale.ROOT, fmt, decimals.on() ? p.getX() : Math.floor(p.getX())));
		out.add("Y: " + String.format(Locale.ROOT, fmt, decimals.on() ? p.getY() : Math.floor(p.getY())));
		out.add("Z: " + String.format(Locale.ROOT, fmt, decimals.on() ? p.getZ() : Math.floor(p.getZ())));
		if (showDirection.on()) {
			Direction d = p.getDirection();
			String axis = switch (d) {
				case NORTH -> " (-Z)";
				case SOUTH -> " (+Z)";
				case EAST -> " (+X)";
				case WEST -> " (-X)";
				default -> "";
			};
			out.add("Facing: " + capitalize(d.getName()) + axis);
		}
		if (showBiome.on() && p.level() != null) {
			String biome = p.level().getBiome(p.blockPosition()).unwrapKey()
				.map(k -> k.identifier().getPath()).orElse("unknown");
			out.add("Biome: " + capitalize(biome.replace('_', ' ')));
		}
		return out;
	}

	private static String capitalize(String s) {
		StringBuilder b = new StringBuilder();
		for (String w : s.split(" ")) {
			if (w.isEmpty()) continue;
			if (!b.isEmpty()) b.append(' ');
			b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return b.toString();
	}
}
