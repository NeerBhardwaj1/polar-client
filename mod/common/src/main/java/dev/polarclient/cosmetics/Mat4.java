package dev.polarclient.cosmetics;

/** Tiny 4x4 matrix helpers (column-major float[16], like OpenGL), so the cosmetics maths is the same on every version. */
final class Mat4 {
	private Mat4() {
	}

	static float[] identity() {
		return new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
	}

	static float[] mul(float[] a, float[] b) {
		float[] o = new float[16];
		for (int c = 0; c < 4; c++) {
			for (int r = 0; r < 4; r++) {
				o[c * 4 + r] = a[r] * b[c * 4] + a[4 + r] * b[c * 4 + 1] + a[8 + r] * b[c * 4 + 2] + a[12 + r] * b[c * 4 + 3];
			}
		}
		return o;
	}

	/** Grows or shrinks by s around the point (x, y, z). */
	static float[] scaleAbout(float s, float x, float y, float z) {
		return new float[] {s, 0, 0, 0, 0, s, 0, 0, 0, 0, s, 0, x - s * x, y - s * y, z - s * z, 1};
	}

	static float[] translate(float x, float y, float z) {
		return new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, x, y, z, 1};
	}

	static float[] rotX(float a) {
		float c = (float) Math.cos(a), s = (float) Math.sin(a);
		return new float[] {1, 0, 0, 0, 0, c, s, 0, 0, -s, c, 0, 0, 0, 0, 1};
	}

	static float[] rotY(float a) {
		float c = (float) Math.cos(a), s = (float) Math.sin(a);
		return new float[] {c, 0, -s, 0, 0, 1, 0, 0, s, 0, c, 0, 0, 0, 0, 1};
	}

	static float[] rotZ(float a) {
		float c = (float) Math.cos(a), s = (float) Math.sin(a);
		return new float[] {c, s, 0, 0, -s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
	}

	/** Like Minecraft's ModelPart.translateAndRotate: move to the pivot (pixels), then turn Z, Y, X (radians). */
	static float[] part(float[] parent, float px, float py, float pz, float rx, float ry, float rz) {
		float[] m = mul(parent, translate(px / 16f, py / 16f, pz / 16f));
		if (rz != 0) m = mul(m, rotZ(rz));
		if (ry != 0) m = mul(m, rotY(ry));
		if (rx != 0) m = mul(m, rotX(rx));
		return m;
	}

	static void point(float[] m, float x, float y, float z, float[] out, int off) {
		out[off] = m[0] * x + m[4] * y + m[8] * z + m[12];
		out[off + 1] = m[1] * x + m[5] * y + m[9] * z + m[13];
		out[off + 2] = m[2] * x + m[6] * y + m[10] * z + m[14];
	}

	static void dir(float[] m, float x, float y, float z, float[] out, int off) {
		float nx = m[0] * x + m[4] * y + m[8] * z;
		float ny = m[1] * x + m[5] * y + m[9] * z;
		float nz = m[2] * x + m[6] * y + m[10] * z;
		float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (l < 1e-6f) l = 1;
		out[off] = nx / l;
		out[off + 1] = ny / l;
		out[off + 2] = nz / l;
	}
}
