package dev.polarclient.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A cosmetic model (wings, hats, back items): bones of boxes like Minecraft's own models, plus animations. The same
 * format and maths as the launcher's wardrobe viewer (launcher/src/renderer/cosmetics3d.js), made by
 * tools/cosmetics. Positions are pixels; one block is 16 pixels; y points down and the face is at -z.
 */
final class CosmeticModel {
	private static final float RAD = (float) (Math.PI / 180);
	private static final String[] FACES = {"top", "bottom", "right", "front", "left", "back"};
	// the face normals, in the order of FACES (Minecraft's model space: y down)
	private static final float[][] NORMALS = {{0, -1, 0}, {0, 1, 0}, {-1, 0, 0}, {0, 0, -1}, {1, 0, 0}, {0, 0, 1}};

	static final class Cube {
		float[] from = new float[3], to = new float[3];
		float[][] faces = new float[6][]; // per FACES entry: {u0, v0, u1, v1} texels, or null
		float inflate;
		boolean tint;
	}

	static final class Bone {
		String name;
		float[] pivot = new float[3], rot = new float[3];
		List<Cube> cubes = new ArrayList<>();
		List<Bone> children = new ArrayList<>();
	}

	static final class Anim {
		String bone, type;
		float[] rot = new float[3], pos = new float[3];
		float speed = 1, speedMove = Float.NaN, phase, idle = 1, move = 1;
		String key;
	}

	final boolean head;
	final int texW, texH, frames, frameTime;
	final boolean glow, translucent;
	final List<Bone> bones = new ArrayList<>();
	final List<Anim> anims = new ArrayList<>();

	CosmeticModel(JsonObject o) {
		head = "head".equals(str(o, "anchor"));
		JsonArray size = o.getAsJsonArray("size");
		texW = size.get(0).getAsInt();
		texH = size.get(1).getAsInt();
		frames = Math.max(1, num(o, "frames", 1));
		frameTime = Math.max(16, num(o, "frameTime", 100));
		glow = o.has("glow") && !o.get("glow").isJsonNull();
		translucent = o.has("translucent") && o.get("translucent").getAsBoolean();
		for (JsonElement b : o.getAsJsonArray("bones")) bones.add(bone(b.getAsJsonObject()));
		if (o.has("anims")) {
			for (JsonElement e : o.getAsJsonArray("anims")) {
				JsonObject a = e.getAsJsonObject();
				Anim an = new Anim();
				an.bone = str(a, "bone");
				an.type = str(a, "type");
				vec(a, "rot", an.rot);
				vec(a, "pos", an.pos);
				an.speed = fnum(a, "speed", 1);
				an.speedMove = fnum(a, "speedMove", Float.NaN);
				an.phase = fnum(a, "phase", 0);
				an.idle = fnum(a, "idle", 1);
				an.move = fnum(a, "move", 1);
				an.key = an.bone + ':' + an.type + ':' + an.phase + ':' + anims.size();
				anims.add(an);
			}
		}
	}

	private static Bone bone(JsonObject o) {
		Bone b = new Bone();
		b.name = str(o, "name");
		vec(o, "pivot", b.pivot);
		vec(o, "rot", b.rot);
		if (o.has("cubes")) {
			for (JsonElement e : o.getAsJsonArray("cubes")) {
				JsonObject c = e.getAsJsonObject();
				Cube cube = new Cube();
				vec(c, "from", cube.from);
				vec(c, "to", cube.to);
				cube.inflate = fnum(c, "inflate", 0);
				cube.tint = c.has("tint") && c.get("tint").getAsBoolean();
				JsonObject faces = c.getAsJsonObject("faces");
				for (int i = 0; i < FACES.length; i++) {
					if (faces != null && faces.has(FACES[i])) {
						JsonArray r = faces.getAsJsonArray(FACES[i]);
						cube.faces[i] = new float[] {r.get(0).getAsFloat(), r.get(1).getAsFloat(), r.get(2).getAsFloat(), r.get(3).getAsFloat()};
					}
				}
				b.cubes.add(cube);
			}
		}
		if (o.has("children")) for (JsonElement e : o.getAsJsonArray("children")) b.children.add(bone(e.getAsJsonObject()));
		return b;
	}

	/**
	 * Poses the model and adds its quads. phases: this player's animation clocks (advanced by dt seconds); moving: 0
	 * standing still .. 1 walking; timeMs: a shared clock for spinning parts; tint: ARGB for the player's colour.
	 */
	void emit(Map<String, Float> phases, float dt, float moving, long timeMs, int tint, RenderBatch base, RenderBatch glowBatch) {
		java.util.HashMap<String, float[]> pose = new java.util.HashMap<>();
		for (Anim a : anims) {
			float[] v = pose.get(a.bone);
			if (v == null) pose.put(a.bone, v = new float[6]);
			if ("spin".equals(a.type)) {
				float t = (timeMs % 3600000L) / 1000f;
				for (int i = 0; i < 3; i++) v[i] += a.rot[i] * t;
				continue;
			}
			float speedMove = Float.isNaN(a.speedMove) ? a.speed : a.speedMove;
			float speed = a.speed + (speedMove - a.speed) * moving;
			Float ph = phases.get(a.key);
			float phase = ((ph == null ? 0 : ph) + dt * speed) % 1000f;
			phases.put(a.key, phase);
			float amp = a.idle + (a.move - a.idle) * moving;
			float s = (float) Math.sin(2 * Math.PI * phase + a.phase) * amp;
			for (int i = 0; i < 3; i++) {
				v[i] += a.rot[i] * s;
				v[3 + i] += a.pos[i] * s;
			}
		}
		for (Bone b : bones) walk(b, Mat4.identity(), pose, tint, base, glowBatch);
	}

	private void walk(Bone b, float[] parent, Map<String, float[]> pose, int tint, RenderBatch base, RenderBatch glowBatch) {
		float[] an = pose.get(b.name);
		float px = b.pivot[0], py = b.pivot[1], pz = b.pivot[2];
		float rx = b.rot[0], ry = b.rot[1], rz = b.rot[2];
		if (an != null) {
			rx += an[0];
			ry += an[1];
			rz += an[2];
			px += an[3];
			py += an[4];
			pz += an[5];
		}
		float[] m = Mat4.part(parent, px, py, pz, rx * RAD, ry * RAD, rz * RAD);
		float[] p = new float[12], n = new float[3];
		for (Cube c : b.cubes) {
			float g = c.inflate;
			float x0 = (c.from[0] - g) / 16f, y0 = (c.from[1] - g) / 16f, z0 = (c.from[2] - g) / 16f;
			float x1 = (c.to[0] + g) / 16f, y1 = (c.to[1] + g) / 16f, z1 = (c.to[2] + g) / 16f;
			// corners as Minecraft numbers them: v0 (x0,y0,z0) v1 (x1,y0,z0) v2 (x1,y1,z0) v3 (x0,y1,z0) v4..v7 the same at z1
			float[][] v = {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
			int[][] order = {{5, 4, 0, 1}, {2, 3, 7, 6}, {0, 4, 7, 3}, {1, 0, 3, 2}, {5, 1, 2, 6}, {4, 5, 6, 7}};
			int color = c.tint ? tint : 0xFFFFFFFF;
			for (int f = 0; f < 6; f++) {
				float[] r = c.faces[f];
				if (r == null) continue;
				float u0 = r[0] / texW, v0 = r[1] / texH, u1 = r[2] / texW, v1 = r[3] / texH;
				if (f == 1) { // Minecraft flips the bottom face's texture
					float t = v0;
					v0 = v1;
					v1 = t;
				}
				for (int k = 0; k < 4; k++) {
					float[] c3 = v[order[f][k]];
					Mat4.point(m, c3[0], c3[1], c3[2], p, k * 3);
				}
				Mat4.dir(m, NORMALS[f][0], NORMALS[f][1], NORMALS[f][2], n, 0);
				float[] us = {u1, u0, u0, u1}, vs = {v0, v0, v1, v1};
				for (int k = 0; k < 4; k++) base.vertex(p[k * 3], p[k * 3 + 1], p[k * 3 + 2], us[k], vs[k], n[0], n[1], n[2], color);
				if (glowBatch != null) {
					for (int k = 0; k < 4; k++) glowBatch.vertex(p[k * 3], p[k * 3 + 1], p[k * 3 + 2], us[k], vs[k], n[0], n[1], n[2], 0xFFFFFFFF);
				}
			}
		}
		for (Bone ch : b.children) walk(ch, m, pose, tint, base, glowBatch);
	}

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	private static int num(JsonObject o, String k, int def) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? def : e.getAsInt();
	}

	private static float fnum(JsonObject o, String k, float def) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? def : e.getAsFloat();
	}

	private static void vec(JsonObject o, String k, float[] out) {
		JsonElement e = o.get(k);
		if (e == null || !e.isJsonArray()) return;
		JsonArray a = e.getAsJsonArray();
		for (int i = 0; i < 3 && i < a.size(); i++) out[i] = a.get(i).getAsFloat();
	}
}
