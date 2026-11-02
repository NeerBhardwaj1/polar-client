package dev.polarclient.gui;

import dev.polarclient.PolarClient;
import dev.polarclient.gui.ui.Theme;
import dev.polarclient.gui.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Random;

/**
 * The animated Polar main-menu backdrop: a pre-rendered voxel night scene in two parallax layers
 * (sky and landscape) with live aurora, aurora reflection on the ice, twinkling stars, a glowing moon,
 * flickering village lights, glints on the ice, drifting mist, snowfall and the odd shooting star.
 * Coordinates inside the layers are texture pixels of the 1920x1080 renders.
 */
public final class MenuBackground {
	public static final MenuBackground INSTANCE = new MenuBackground();

	private static final Identifier SKY = tex("sky");
	private static final Identifier SCENE = tex("scene");
	private static final Identifier AURORA = tex("aurora");
	private static final Identifier GLOW = tex("glow");
	private static final Identifier MOON = tex("moon");
	private static final int TW = 1920, TH = 1080, AW = 2048, AH = 512;

	private static Identifier tex(String name) {
		return Identifier.fromNamespaceAndPath("polarclient", "textures/gui/menu/" + name + ".png");
	}

	private final long start = System.nanoTime();
	private long last = System.nanoTime();
	private float px, py; // smoothed mouse parallax
	private final Random random = new Random(42);

	private static final int FLAKES = 150;
	private final float[] fx = new float[FLAKES], fy = new float[FLAKES], fz = new float[FLAKES], fphase = new float[FLAKES];
	private boolean flakesReady;
	private float shootingAt = 6f, shootX, shootY;

	private MenuBackground() {
	}

	public void render(GuiGraphicsExtractor g, int w, int h, int mouseX, int mouseY) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		float t = (now - start) / 1e9f;
		px += ((mouseX / (float) Math.max(1, w) - 0.5f) - px) * Math.min(1, dt * 2.5f);
		py += ((mouseY / (float) Math.max(1, h) - 0.5f) - py) * Math.min(1, dt * 2.5f);

		float cover = Math.max(w / (float) TW, h / (float) TH);
		float k = cover * (1.085f + 0.012f * (float) Math.sin(t * 0.08));
		float driftX = (float) Math.sin(t * 0.045) * 9f, driftY = (float) Math.sin(t * 0.06 + 1) * 3f;

		// ---- far layer: sky, stars, moon halo, aurora
		float farX = -px * 6 + driftX * 0.5f, farY = -py * 4 + driftY * 0.5f;
		begin(g, w, h, k, farX, farY);
		g.blit(SKY, 0, 0, TW, TH, 0f, 1f, 0f, 1f);
		drawStars(g, t);
		float moonPulse = 0.85f + 0.15f * (float) Math.sin(t * 0.7);
		glow(g, BackgroundData.MOON_X * TW, BackgroundData.MOON_Y * TH, 190, 190, Theme.alpha(0xFFB9CEFF, 0.22f * moonPulse));
		glow(g, BackgroundData.MOON_X * TW, BackgroundData.MOON_Y * TH, 80, 80, Theme.alpha(0xFFE6EEFF, 0.18f * moonPulse));
		drawAurora(g, t, false);
		g.pose().popMatrix();

		// ---- near layer: aurora reflection (seen through the ice), landscape, lights, glints, mist
		begin(g, w, h, k, -px * 16 + driftX, -py * 8 + driftY);
		drawAurora(g, t, true);
		g.blit(SCENE, 0, 0, TW, TH, 0f, 1f, 0f, 1f);
		drawLights(g, t);
		drawSparkles(g, t);
		drawMist(g, t);
		g.pose().popMatrix();

		// ---- screen space: legibility shading, then the moon (kept bright, it sits in open sky), shooting star, snow
		g.fillGradient(0, 0, w, h / 4, 0x4D000000, 0x00000000);
		g.fillGradient(0, h * 2 / 3, w, h, 0x00000000, 0x8C000000);
		float edge = w * 0.17f;
		Ui.hGradient(g, 0, 0, edge, h, 0x46000000, 0x00000000);
		Ui.hGradient(g, w - edge, 0, edge, h, 0x00000000, 0x46000000);
		begin(g, w, h, k, farX, farY);
		drawMoon(g, moonPulse);
		g.pose().popMatrix();
		drawShootingStar(g, w, h, t);
		if (PolarClient.modules() == null || PolarClient.modules().client.snow.on()) drawSnow(g, w, h, dt, t);
	}

	private void begin(GuiGraphicsExtractor g, int w, int h, float k, float ox, float oy) {
		g.pose().pushMatrix();
		g.pose().translate(w / 2f + ox, h / 2f + oy);
		g.pose().scale(k);
		g.pose().translate(-TW / 2f, -TH / 2f);
	}

	/** Soft glow sprite in the current (texture-pixel) space. */
	private static void glow(GuiGraphicsExtractor g, float cx, float cy, float rx, float ry, int argb) {
		if ((argb >>> 24) == 0) return;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(rx / 64f, ry / 64f);
		g.blit(RenderPipelines.GUI_TEXTURED, GLOW, -64, -64, 0, 0, 128, 128, 128, 128, 128, 128, argb);
		g.pose().popMatrix();
	}

	private void drawStars(GuiGraphicsExtractor g, float t) {
		float[][] stars = BackgroundData.STARS;
		for (float[] s : stars) {
			float tw = 0.5f + 0.5f * (float) Math.sin(t * (1.1f + s[2] * 0.8f) + s[3]);
			float a = 0.25f + 0.75f * tw * tw;
			float x = s[0] * TW, y = s[1] * TH, size = s[2];
			glow(g, x, y, size * 6, size * 6, Theme.alpha(0xFFCFE0FF, 0.55f * a));
			int c = Theme.alpha(0xFFFFFFFF, a);
			int r = size > 1.3f ? 2 : 1;
			g.fill(Math.round(x) - r / 2, Math.round(y) - r / 2, Math.round(x) - r / 2 + r, Math.round(y) - r / 2 + r, c);
		}
	}

	/** The blocky moon face, drawn over the aurora so it stays crisp. */
	private void drawMoon(GuiGraphicsExtractor g, float pulse) {
		float mx = BackgroundData.MOON_X * TW, my = BackgroundData.MOON_Y * TH;
		glow(g, mx, my, 130, 130, Theme.alpha(0xFFDCE7FF, 0.20f * pulse));
		int size = 100;
		g.blit(RenderPipelines.GUI_TEXTURED, MOON, Math.round(mx - size / 2f), Math.round(my - size / 2f), 0, 0, size, size,
			128, 128, 128, 128, 0xFFFFFFFF);
	}

	/** Two drifting aurora curtains; mirrored around the lake's top edge for the reflection. */
	private void drawAurora(GuiGraphicsExtractor g, float t, boolean reflection) {
		float mirror = BackgroundData.LAKE_TOP * TH;
		band(g, t, 14f, 1.35f, -70 + 10 * (float) Math.sin(t * 0.21), 600, 0.85f * (0.78f + 0.22f * (float) Math.sin(t * 0.37)), reflection, mirror, 0);
		band(g, t, -9f, 0.95f, -20 + 14 * (float) Math.sin(t * 0.17 + 2), 480, 0.45f * (0.7f + 0.3f * (float) Math.sin(t * 0.29 + 1)), reflection, mirror, 700);
	}

	private void band(GuiGraphicsExtractor g, float t, float speed, float sx, float top, int height, float alpha,
					  boolean reflection, float mirror, float phase) {
		int stripW = Math.round(AW * sx);
		float off = ((t * speed + phase) % stripW + stripW) % stripW;
		int a = reflection ? Math.round(alpha * 0.9f * 255) : Math.round(alpha * 255);
		int color = (Math.min(255, a) << 24) | 0xFFFFFF;
		int y = Math.round(top);
		if (reflection) y = Math.round(2 * mirror - top - height);
		for (float x = -off - stripW; x < TW + stripW; x += stripW) {
			if (reflection) {
				g.blit(RenderPipelines.GUI_TEXTURED, AURORA, Math.round(x), y, 0, AH, stripW, height, AW, -AH, AW, AH, color);
			} else {
				g.blit(RenderPipelines.GUI_TEXTURED, AURORA, Math.round(x), y, 0, 0, stripW, height, AW, AH, AW, AH, color);
			}
		}
	}

	private void drawLights(GuiGraphicsExtractor g, float t) {
		float[][] lights = BackgroundData.LIGHTS;
		for (int i = 0; i < lights.length; i++) {
			float[] l = lights[i];
			float flick = 0.72f + 0.16f * (float) Math.sin(t * 7.3f + i * 1.7f) + 0.08f * (float) Math.sin(t * 13.1f + i * 2.9f)
				+ 0.04f * (float) Math.sin(t * 2.3f + i);
			float r = 9 + (float) Math.sqrt(l[2]) * 3.2f;
			float x = l[0] * TW, y = l[1] * TH;
			glow(g, x, y, r * 2.6f, r * 2.2f, Theme.alpha(0xFFFFA64D, 0.30f * flick));
			glow(g, x, y, r * 0.9f, r * 0.9f, Theme.alpha(0xFFFFE0A8, 0.30f * flick));
		}
	}

	private void drawSparkles(GuiGraphicsExtractor g, float t) {
		float[][] sp = BackgroundData.SPARKLES;
		for (int i = 0; i < sp.length; i++) {
			float s = (float) Math.sin(t * 1.3f + i * 2.37f);
			if (s <= 0.6f) continue;
			float a = (float) Math.pow((s - 0.6f) / 0.4f, 3);
			float x = sp[i][0] * TW, y = sp[i][1] * TH;
			glow(g, x, y, 9, 9, Theme.alpha(0xFFDDEBFF, 0.8f * a));
			g.fill(Math.round(x) - 6, Math.round(y), Math.round(x) + 6, Math.round(y) + 1, Theme.alpha(0xFFFFFFFF, 0.6f * a));
			g.fill(Math.round(x), Math.round(y) - 4, Math.round(x) + 1, Math.round(y) + 4, Theme.alpha(0xFFFFFFFF, 0.6f * a));
		}
	}

	private void drawMist(GuiGraphicsExtractor g, float t) {
		float y = BackgroundData.LAKE_TOP * TH + 6;
		glow(g, 960 + (float) Math.sin(t * 0.031) * 320, y, 980, 46, Theme.alpha(0xFFA9C4FF, 0.10f));
		glow(g, 960 + (float) Math.sin(t * 0.023 + 2.4) * 420, y + 30, 760, 34, Theme.alpha(0xFFBCD2FF, 0.08f));
	}

	private void drawShootingStar(GuiGraphicsExtractor g, int w, int h, float t) {
		if (t < shootingAt) return;
		float p = (t - shootingAt) / 0.9f;
		if (p == 0 || shootX == 0) {
			shootX = w * (0.25f + random.nextFloat() * 0.5f);
			shootY = h * (0.04f + random.nextFloat() * 0.14f);
		}
		if (p >= 1) {
			shootingAt = t + 9 + random.nextFloat() * 10;
			shootX = 0;
			return;
		}
		float len = Math.min(w, h) * 0.28f;
		float hx = shootX - len * p, hy = shootY + len * 0.45f * p;
		float fade = p < 0.15f ? p / 0.15f : 1 - (p - 0.15f) / 0.85f;
		for (int i = 0; i < 14; i++) {
			float f = i / 14f;
			Ui.glow(g, hx + len * 0.35f * f, hy - len * 0.16f * f, 2.2f * (1 - f) + 0.6f, 2.2f * (1 - f) + 0.6f,
				Theme.alpha(0xFFE8F1FF, fade * (1 - f) * 0.9f));
		}
	}

	private void resetFlake(int i, int w, int h, boolean anywhere) {
		fz[i] = random.nextFloat();
		fx[i] = random.nextFloat() * w;
		fy[i] = anywhere ? random.nextFloat() * h : -6;
		fphase[i] = random.nextFloat() * 6.283f;
	}

	private void drawSnow(GuiGraphicsExtractor g, int w, int h, float dt, float t) {
		if (!flakesReady) {
			for (int i = 0; i < FLAKES; i++) resetFlake(i, w, h, true);
			flakesReady = true;
		}
		for (int i = 0; i < FLAKES; i++) {
			float z = fz[i];
			float speed = 6 + z * z * 34;
			fy[i] += speed * dt;
			fx[i] += ((float) Math.sin(t * (0.6f + z) + fphase[i]) * (4 + z * 10) - 3) * dt;
			if (fy[i] > h + 8 || fx[i] < -8 || fx[i] > w + 8) {
				resetFlake(i, w, h, false);
				continue;
			}
			if (z > 0.86f) {
				// close, out-of-focus flakes
				float r = 1.6f + (z - 0.86f) * 18;
				Ui.glow(g, fx[i], fy[i], r, r, Theme.alpha(0xFFFFFFFF, 0.55f));
			} else {
				float r = 0.35f + z * 0.9f;
				Ui.circle(g, fx[i], fy[i], r, Theme.alpha(0xFFFFFFFF, 0.45f + z * 0.5f));
			}
		}
	}
}
