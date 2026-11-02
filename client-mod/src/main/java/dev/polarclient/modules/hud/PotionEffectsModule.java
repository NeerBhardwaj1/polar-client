package dev.polarclient.modules.hud;

import dev.polarclient.module.HudModule;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.util.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.List;

public class PotionEffectsModule extends HudModule {
	private static final int ROW = 20;
	private final BooleanSetting showName = add(new BooleanSetting("name", "Show name", true));
	private final BooleanSetting showDuration = add(new BooleanSetting("duration", "Show duration", true));
	private final BooleanSetting blink = add(new BooleanSetting("blink", "Blink when ending", true));
	private final BooleanSetting hideAmbient = add(new BooleanSetting("hide_ambient", "Hide beacon effects", false));

	public PotionEffectsModule() {
		super("potion_effects", "Potion Effects", "Active effects with icons and time left.", true, 384, 4);
	}

	private List<MobEffectInstance> effects(boolean preview) {
		List<MobEffectInstance> out = new ArrayList<>();
		var p = Minecraft.getInstance().player;
		if (p != null) {
			for (MobEffectInstance e : p.getActiveEffects()) {
				if (hideAmbient.on() && e.isAmbient()) continue;
				out.add(e);
			}
		}
		if (out.isEmpty() && preview) {
			out.add(new MobEffectInstance(MobEffects.SPEED, 1800, 1));
			out.add(new MobEffectInstance(MobEffects.STRENGTH, 900, 0));
			out.add(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 3600, 0));
		}
		return out;
	}

	@Override
	public boolean hasContent() {
		return !effects(false).isEmpty();
	}

	private String name(MobEffectInstance e) {
		String n = e.getEffect().value().getDisplayName().getString();
		int amp = e.getAmplifier() + 1;
		return amp > 1 ? n + " " + roman(amp) : n;
	}

	private String duration(MobEffectInstance e) {
		var level = Minecraft.getInstance().level;
		float tickrate = level != null ? level.tickRateManager().tickrate() : 20f;
		return MobEffectUtil.formatDuration(e, 1.0f, tickrate).getString();
	}

	private static String roman(int n) {
		return switch (n) {
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			default -> String.valueOf(n);
		};
	}

	@Override
	public int width(boolean preview) {
		int w = 0;
		for (MobEffectInstance e : effects(preview)) {
			int tw = 0;
			if (showName.on()) tw = Draw.font().width(name(e));
			if (showDuration.on()) tw = Math.max(tw, Draw.font().width(duration(e)));
			w = Math.max(w, tw);
		}
		return 18 + (w > 0 ? 4 + w : 0);
	}

	@Override
	public int height(boolean preview) {
		return Math.max(1, effects(preview).size()) * ROW - 2;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, boolean preview) {
		int y = 0;
		for (MobEffectInstance e : effects(preview)) {
			boolean ending = blink.on() && !e.isInfiniteDuration() && e.endsWithin(200);
			float alpha = ending ? 0.45f + 0.55f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 220.0)) : 1f;
			g.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(e.getEffect()), 0, y, 18, 18, alpha);
			int tx = 22;
			if (showName.on() && showDuration.on()) {
				g.text(Draw.font(), name(e), tx, y, Draw.alpha(0xFFFFFFFF, alpha), true);
				g.text(Draw.font(), duration(e), tx, y + 10, Draw.alpha(0xFFA9C5E8, alpha), true);
			} else if (showName.on() || showDuration.on()) {
				String s = showName.on() ? name(e) : duration(e);
				g.text(Draw.font(), s, tx, y + 5, Draw.alpha(0xFFFFFFFF, alpha), true);
			}
			y += ROW;
		}
	}
}
