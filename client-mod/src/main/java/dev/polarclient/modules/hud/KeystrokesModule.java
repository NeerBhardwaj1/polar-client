package dev.polarclient.modules.hud;

import dev.polarclient.module.HudModule;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ColorSetting;
import dev.polarclient.util.ClickTracker;
import dev.polarclient.util.Draw;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashMap;
import java.util.Map;

public class KeystrokesModule extends HudModule {
	private static final int KEY = 20, GAP = 2, WIDTH = KEY * 3 + GAP * 2;

	private final BooleanSetting mouse = add(new BooleanSetting("mouse", "Show mouse buttons", true));
	private final BooleanSetting cps = add(new BooleanSetting("cps", "Show CPS on mouse buttons", true));
	private final BooleanSetting space = add(new BooleanSetting("space", "Show space bar", true));
	private final ColorSetting keyColor = add(new ColorSetting("key_color", "Key color", 0x880B1426, true));
	private final ColorSetting pressedColor = add(new ColorSetting("pressed_color", "Pressed color", 0xDDCFE5FF, true));
	private final ColorSetting textColor = add(new ColorSetting("text_color", "Text color", 0xFFFFFFFF));
	private final ColorSetting pressedTextColor = add(new ColorSetting("pressed_text_color", "Pressed text color", 0xFF0B1426));
	private final BooleanSetting fade = add(new BooleanSetting("fade", "Smooth fade", true));

	private final Map<String, Float> anim = new HashMap<>();
	private long lastFrame = System.nanoTime();
	private float frameDelta;

	public KeystrokesModule() {
		super("keystrokes", "Keystrokes", "Shows WASD, mouse clicks and jump as you press them.", true, 4, 82);
	}

	@Override
	public int width(boolean preview) {
		return WIDTH;
	}

	@Override
	public int height(boolean preview) {
		int h = KEY * 2 + GAP;
		if (mouse.on()) h += GAP + 18;
		if (space.on()) h += GAP + 10;
		return h;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, boolean preview) {
		long now = System.nanoTime();
		frameDelta = Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		var o = Minecraft.getInstance().options;
		key(g, "w", o.keyUp, KEY + GAP, 0, KEY, KEY, null);
		key(g, "a", o.keyLeft, 0, KEY + GAP, KEY, KEY, null);
		key(g, "s", o.keyDown, KEY + GAP, KEY + GAP, KEY, KEY, null);
		key(g, "d", o.keyRight, (KEY + GAP) * 2, KEY + GAP, KEY, KEY, null);
		int y = KEY * 2 + GAP * 2;
		if (mouse.on()) {
			int half = (WIDTH - GAP) / 2;
			key(g, "lmb", o.keyAttack, 0, y, half, 18, cps.on() ? ClickTracker.leftCps() + " CPS" : "LMB");
			key(g, "rmb", o.keyUse, half + GAP, y, WIDTH - half - GAP, 18, cps.on() ? ClickTracker.rightCps() + " CPS" : "RMB");
			y += 18 + GAP;
		}
		if (space.on()) {
			float t = press("space", o.keyJump.isDown());
			Draw.round(g, 0, y, WIDTH, 10, 2, Draw.lerp(keyColor.argb(), pressedColor.argb(), t));
			int bar = Draw.lerp(textColor.argb(), pressedTextColor.argb(), t);
			g.fill(WIDTH / 2 - 14, y + 4, WIDTH / 2 + 14, y + 5, bar);
		}
	}

	private void key(GuiGraphicsExtractor g, String id, KeyMapping mapping, int x, int y, int w, int h, String override) {
		float t = press(id, mapping.isDown());
		Draw.round(g, x, y, w, h, 2, Draw.lerp(keyColor.argb(), pressedColor.argb(), t));
		String label = override != null ? override : Draw.ellipsize(mapping.getTranslatedKeyMessage().getString(), w - 4);
		int color = Draw.lerp(textColor.argb(), pressedTextColor.argb(), t);
		g.text(Draw.font(), label, x + (w - Draw.font().width(label)) / 2, y + (h - 8) / 2, color, false);
	}

	private float press(String id, boolean down) {
		float target = down ? 1f : 0f;
		if (!fade.on()) {
			anim.put(id, target);
			return target;
		}
		float v = Draw.approach(anim.getOrDefault(id, 0f), target, 22f, frameDelta);
		anim.put(id, v);
		return v;
	}
}
