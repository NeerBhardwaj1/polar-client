package dev.polarclient.modules.hud;

import dev.polarclient.gfx.Gfx;
import dev.polarclient.module.HudModule;
import dev.polarclient.platform.EffectView;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.util.Draw;

import java.util.List;

public class PotionEffectsModule extends HudModule {
	private static final int ROW = 20;
	private final BooleanSetting showName = add(new BooleanSetting("name", "Show name", true));
	private final BooleanSetting showDuration = add(new BooleanSetting("duration", "Show duration", true));
	private final BooleanSetting blink = add(new BooleanSetting("blink", "Blink when ending", true));
	private final BooleanSetting hideAmbient = add(new BooleanSetting("hide_ambient", "Hide beacon effects", false));

	public PotionEffectsModule() {
		super("potion_effects", "Potion Effects", "Active effects and time left.", true, 384, 4);
	}

	private List<EffectView> effects(boolean preview) {
		List<EffectView> out = p().hasPlayer() ? p().effects(hideAmbient.on()) : null;
		if ((out == null || out.isEmpty()) && preview) out = p().previewEffects();
		return out == null ? java.util.Collections.<EffectView>emptyList() : out;
	}

	@Override
	public boolean hasContent() {
		return !effects(false).isEmpty();
	}

	@Override
	public int width(boolean preview) {
		int w = 0;
		for (EffectView e : effects(preview)) {
			int tw = 0;
			if (showName.on()) tw = Draw.width(e.name);
			if (showDuration.on()) tw = Math.max(tw, Draw.width(e.duration));
			w = Math.max(w, tw);
		}
		return 18 + (w > 0 ? 4 + w : 0);
	}

	@Override
	public int height(boolean preview) {
		return Math.max(1, effects(preview).size()) * ROW - 2;
	}

	@Override
	protected void draw(Gfx g, boolean preview) {
		int y = 0;
		for (EffectView e : effects(preview)) {
			boolean ending = blink.on() && e.endingSoon;
			float alpha = ending ? 0.45f + 0.55f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 220.0)) : 1f;
			g.effectIcon(e, 0, y, 18, alpha);
			int tx = 22;
			if (showName.on() && showDuration.on()) {
				g.text(e.name, tx, y, Draw.alpha(0xFFFFFFFF, alpha), true);
				g.text(e.duration, tx, y + 10, Draw.alpha(0xFFA9C5E8, alpha), true);
			} else if (showName.on() || showDuration.on()) {
				String s = showName.on() ? e.name : e.duration;
				g.text(s, tx, y + 5, Draw.alpha(0xFFFFFFFF, alpha), true);
			}
			y += ROW;
		}
	}
}
