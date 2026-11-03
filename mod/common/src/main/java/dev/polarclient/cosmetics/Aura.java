package dev.polarclient.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * An aura: particles around a player, simulated here (the same rules as the launcher's wardrobe viewer) and drawn as
 * sprites that face the camera. Positions are world coordinates, so particles that fall or drift stay behind as you
 * walk; swirling ones circle wherever you are.
 */
final class Aura {
	/** The settings from the catalogue (ranges are {min, max}). */
	static final class Settings {
		float rate = 6;
		float[] life = {1.5f, 2.5f}, size = {0.08f, 0.14f}, radius = {0.3f, 0.7f}, height = {0, 1.9f};
		float[] vx = {0, 0}, vy = {0, 0}, vz = {0, 0}, spin = {0, 0}, swirl = {0, 0};
		float gravity;
		boolean shrink, glow;
		int[] colors = {0xFFFFFFFF};
		int frames = 1;

		Settings(JsonObject o) {
			rate = f(o, "rate", rate);
			life = range(o, "life", life);
			size = range(o, "size", size);
			radius = range(o, "radius", radius);
			height = range(o, "height", height);
			vx = range(o, "vx", vx);
			vy = range(o, "vy", vy);
			vz = range(o, "vz", vz);
			spin = range(o, "spin", spin);
			swirl = range(o, "swirl", swirl);
			gravity = f(o, "gravity", 0);
			shrink = o.has("shrink") && o.get("shrink").getAsBoolean();
			glow = o.has("glow") && o.get("glow").getAsBoolean();
			frames = Math.max(1, (int) f(o, "frames", 1));
			if (o.has("colors") && o.get("colors").isJsonArray()) {
				JsonArray a = o.getAsJsonArray("colors");
				List<Integer> list = new ArrayList<>();
				for (JsonElement e : a) {
					int c = Cosmetics.parseColor(e.getAsString());
					if (c != 0) list.add(c);
				}
				if (!list.isEmpty()) {
					colors = new int[list.size()];
					for (int i = 0; i < colors.length; i++) colors[i] = list.get(i);
				}
			}
		}

		private static float f(JsonObject o, String k, float def) {
			JsonElement e = o.get(k);
			return e == null || e.isJsonNull() ? def : e.getAsFloat();
		}

		private static float[] range(JsonObject o, String k, float[] def) {
			JsonElement e = o.get(k);
			if (e == null || e.isJsonNull()) return def;
			if (e.isJsonArray()) {
				JsonArray a = e.getAsJsonArray();
				return new float[] {a.get(0).getAsFloat(), a.get(a.size() - 1).getAsFloat()};
			}
			float v = e.getAsFloat();
			return new float[] {v, v};
		}
	}

	private static final class Particle {
		double x, y, z;
		float vx, vy, vz, age, life, size, spin, rot, swirl, ang, rad, height;
		int color, frame;
	}

	private static final int MAX = 160;
	private final Random random = new Random();
	private final List<Particle> particles = new ArrayList<>();
	private float spawn;

	private float rand(float[] r) {
		return r[0] + random.nextFloat() * (r[1] - r[0]);
	}

	/** Moves the particles on by dt seconds; (cx, cy, cz) is where the player's feet are now. */
	void step(Settings s, float dt, double cx, double cy, double cz) {
		spawn += dt * s.rate;
		while (spawn >= 1 && particles.size() < MAX) {
			spawn -= 1;
			Particle p = new Particle();
			p.ang = random.nextFloat() * (float) (Math.PI * 2);
			p.rad = rand(s.radius);
			p.height = rand(s.height);
			p.x = cx + Math.cos(p.ang) * p.rad;
			p.y = cy + p.height;
			p.z = cz + Math.sin(p.ang) * p.rad;
			p.vx = rand(s.vx);
			p.vy = rand(s.vy);
			p.vz = rand(s.vz);
			p.life = rand(s.life);
			p.size = rand(s.size);
			p.spin = rand(s.spin) * (float) (Math.PI / 180);
			p.rot = random.nextFloat() * 6.28f;
			p.swirl = rand(s.swirl);
			p.color = s.colors[random.nextInt(s.colors.length)];
			p.frame = random.nextInt(s.frames);
			particles.add(p);
		}
		if (spawn > 1) spawn = 1;
		for (Iterator<Particle> it = particles.iterator(); it.hasNext();) {
			Particle p = it.next();
			p.age += dt;
			if (p.age >= p.life) {
				it.remove();
				continue;
			}
			p.vy -= s.gravity * dt;
			if (p.swirl != 0) {
				p.ang += p.swirl * dt;
				p.height += p.vy * dt;
				p.x = cx + Math.cos(p.ang) * p.rad;
				p.z = cz + Math.sin(p.ang) * p.rad;
				p.y = cy + p.height;
			} else {
				p.x += p.vx * dt;
				p.y += p.vy * dt;
				p.z += p.vz * dt;
			}
			p.rot += p.spin * dt;
		}
	}

	boolean empty() {
		return particles.isEmpty();
	}

	/**
	 * Camera-facing quads, positioned relative to (ox, oy, oz) (the player's render position): the renderer draws them
	 * from there without turning. camYaw/camPitch: where the camera looks, in degrees (Minecraft's convention).
	 */
	void emit(Settings s, double ox, double oy, double oz, float camYaw, float camPitch, RenderBatch out) {
		double yaw = Math.toRadians(camYaw), pitch = Math.toRadians(camPitch);
		float rx = (float) -Math.cos(yaw), ry = 0, rz = (float) -Math.sin(yaw);
		float fx = (float) (-Math.sin(yaw) * Math.cos(pitch)), fy = (float) -Math.sin(pitch), fz = (float) (Math.cos(yaw) * Math.cos(pitch));
		float ux = ry * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - ry * fx;
		float fw = 1f / s.frames;
		for (Particle p : particles) {
			float t = p.age / p.life;
			float alpha = Math.min(1, t * 5) * Math.min(1, (1 - t) * 3);
			float size = p.size * (s.shrink ? 1 - t * 0.6f : 1);
			float cr = (float) Math.cos(p.rot), sr = (float) Math.sin(p.rot);
			float px = (float) (p.x - ox), py = (float) (p.y - oy), pz = (float) (p.z - oz);
			int a = Math.round(alpha * ((p.color >>> 24) & 255));
			int color;
			if (s.glow) { // added light: fade by dimming
				int r = Math.round(((p.color >> 16) & 255) * alpha), g = Math.round(((p.color >> 8) & 255) * alpha), b = Math.round((p.color & 255) * alpha);
				color = 0xFF000000 | r << 16 | g << 8 | b;
			} else {
				color = a << 24 | (p.color & 0xFFFFFF);
			}
			float u0 = p.frame * fw, u1 = u0 + fw;
			float[][] corners = {{-1, 1}, {1, 1}, {1, -1}, {-1, -1}};
			float[] us = {u0, u1, u1, u0}, vs = {0, 0, 1, 1};
			for (int k = 0; k < 4; k++) {
				float dx = corners[k][0], dy = corners[k][1];
				float qx = dx * cr - dy * sr, qy = dx * sr + dy * cr;
				out.vertex(px + (rx * qx + ux * qy) * size, py + (ry * qx + uy * qy) * size, pz + (rz * qx + uz * qy) * size, us[k], vs[k], -fx, -fy, -fz, color);
			}
		}
	}
}
