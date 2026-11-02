package dev.polarclient.modules.visual;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.platform.PolarKey;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ColorSetting;
import dev.polarclient.util.Popups;

/**
 * The game's own hitboxes (F3+B), in your colour, optionally for players only, toggled with a key (H). Switching the
 * module on shows them; F3+B and the key keep the module in step. The game still hides them where the server turns on
 * reduced debug info.
 */
public class HitboxesModule extends Module {
	public final ColorSetting color = add(new ColorSetting("color", "Colour", 0xFFFFFFFF));
	public final BooleanSetting playersOnly = add(new BooleanSetting("players_only", "Players only", false));
	public final BooleanSetting lookDirection = add(new BooleanSetting("look_direction", "Look direction line", true));

	/** What the game was last set to by us; null before the first tick in a world. */
	private Boolean synced;

	public HitboxesModule() {
		super("hitboxes", "Hitboxes", "Coloured hitboxes. Toggle with H.", Category.VISUAL, false);
	}

	@Override
	public void tick() {
		while (p().consumePolarKey(PolarKey.HITBOXES)) {
			if (p().inWorld()) setEnabled(!isEnabled());
		}
		if (!p().inWorld()) return;
		boolean shown = p().hitboxesShown();
		if (synced == null) {
			// first tick: either side being on turns both on (newer versions remember F3+B between sessions)
			synced = shown || isEnabled();
			if (synced != isEnabled()) setEnabled(synced);
			if (synced != shown && !p().setHitboxesShown(synced)) refused();
			return;
		}
		if (isEnabled() != synced) {
			// switched in the menu or with the key
			synced = isEnabled();
			if (shown != synced && !p().setHitboxesShown(synced)) refused();
		} else if (shown != synced) {
			// F3+B
			synced = shown;
			setEnabled(shown);
		}
	}

	private void refused() {
		synced = false;
		setEnabled(false);
		Popups.show("This server doesn't allow hitboxes", 0xFFFF6B6B, 2500);
	}

	/** Whether to skip an entity's hitbox (players only). */
	public boolean skips(boolean isPlayer) {
		return isEnabled() && playersOnly.on() && !isPlayer;
	}

	/** The main box colour (ARGB) for the game's white box, or the game's own when off. */
	public int boxColor(int vanilla) {
		return isEnabled() ? color.argb() : vanilla;
	}

	public boolean hidesLook() {
		return isEnabled() && !lookDirection.on();
	}
}
