package dev.polarclient.modules;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ModeSetting;

import java.util.Locale;

/**
 * Health after the names above other players' heads ("Steve 8.5❤"), green, yellow or red by how hurt they are, with
 * absorption in gold. Optionally over mobs within 16 blocks too. Each version adds {@link #suffix} to the name tag
 * (never to the scoreboard line under it) and, for mobs, asks {@link #showsMob}.
 */
public class HealthIndicatorModule extends Module {
	public static final double MOB_DISTANCE = 16;

	public final ModeSetting style = add(new ModeSetting("style", "Style", "Hearts", "Hearts", "Points", "Percent"));
	public final BooleanSetting absorption = add(new BooleanSetting("absorption", "Show absorption", true));
	public final BooleanSetting mobs = add(new BooleanSetting("mobs", "Show on mobs", false));

	public HealthIndicatorModule() {
		super("health_indicator", "Health Indicator", "Show players' health next to their name tags.", Category.COMBAT, false);
	}

	/**
	 * The text to add after a name tag, with its colours as Minecraft formatting codes and a space in front, or null to
	 * add nothing (module off, or health the server hides or fakes).
	 */
	public String suffix(float health, float maxHealth, float absorptionAmount) {
		if (!isEnabled() || !(health > 0) || !(maxHealth > 0) || Float.isInfinite(health) || Float.isInfinite(maxHealth)) return null;
		float part = Math.min(1f, health / maxHealth);
		String color = part > 0.6f ? "§a" : part > 0.3f ? "§e" : "§c";
		String text;
		if (style.is("Points")) text = number(health) + "/" + number(maxHealth);
		else if (style.is("Percent")) text = Math.round(part * 100) + "%";
		else text = number(health / 2f) + "❤";
		String out = " " + color + text;
		if (absorption.on() && absorptionAmount > 0 && !Float.isInfinite(absorptionAmount)) {
			out += " §6+" + (style.is("Hearts") ? number(absorptionAmount / 2f) + "❤" : number(absorptionAmount));
		}
		return out;
	}

	/** Whether to give a mob a health tag; distSq: its squared distance from the camera. */
	public boolean showsMob(double distSq) {
		return isEnabled() && mobs.on() && distSq <= MOB_DISTANCE * MOB_DISTANCE;
	}

	/** 8 for 8.0, 8.5 for 8.5. */
	static String number(float v) {
		float r = Math.round(v * 10f) / 10f;
		return Math.abs(r - Math.round(r)) < 0.05f ? String.valueOf(Math.round(r)) : String.format(Locale.ROOT, "%.1f", r);
	}
}
