package dev.polarclient.modules.hud;

import dev.polarclient.gfx.Gfx;
import dev.polarclient.module.Category;
import dev.polarclient.module.HudModule;
import dev.polarclient.util.Draw;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ColorSetting;

/** A bar that fills as your attack recharges (1.9 combat). */
public class AttackCooldownModule extends HudModule {
	private final ColorSetting color = add(new ColorSetting("color", "Bar color", 0xFF5CC8FF));
	private final BooleanSetting hideWhenFull = add(new BooleanSetting("hide_full", "Hide when ready", true));

	public AttackCooldownModule() {
		super("attack_cooldown", "Attack Cooldown", "Show when your attack is ready.", Category.COMBAT, false, 222, 150);
	}

	@Override
	public String unavailableReason() {
		return p().hasAttackCooldown() ? null : "Minecraft " + p().minecraftVersion() + " has no attack cooldown.";
	}

	@Override
	public int width(boolean preview) {
		return 36;
	}

	@Override
	public int height(boolean preview) {
		return 5;
	}

	@Override
	public boolean hasContent() {
		return p().hasPlayer() && !(hideWhenFull.on() && p().attackStrength() >= 1f);
	}

	@Override
	protected void draw(Gfx g, boolean preview) {
		float f = p().hasPlayer() ? p().attackStrength() : 0.65f;
		Draw.round(g, 0, 0, 36, 5, 2, 0x990B1426);
		int fill = Math.round(34 * Math.max(0, Math.min(1, f)));
		if (fill > 0) Draw.round(g, 1, 1, fill, 3, 1, f >= 1 ? 0xFF5CF2A0 : color.argb());
	}
}
