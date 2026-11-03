package dev.polarclient.cosmetics;

/**
 * Quads for one texture and one kind of drawing, ready for the version's renderer. Every vertex has 8 floats
 * (x, y, z in blocks, u, v from 0 to 1, normal x, y, z) and a colour (ARGB). Four vertices per quad.
 */
public final class RenderBatch {
	/** Normal drawing with see-through cut-outs, back faces culled. */
	public static final int CUTOUT = 0;
	/** Translucent, back faces culled. */
	public static final int TRANSLUCENT = 1;
	/** Light-emitting: full-bright and added on top (colour already multiplied by its alpha). */
	public static final int GLOW = 2;
	/** Translucent with both sides drawn (aura sprites). */
	public static final int TRANSLUCENT_BOTH = 3;

	public final Object texture;
	public final int layer;
	float[] data = new float[8 * 64];
	int[] colors = new int[64];
	int count;

	RenderBatch(Object texture, int layer) {
		this.texture = texture;
		this.layer = layer;
	}

	/** How many vertices (a multiple of 4). */
	public int vertices() {
		return count;
	}

	public float x(int i) {
		return data[i * 8];
	}

	public float y(int i) {
		return data[i * 8 + 1];
	}

	public float z(int i) {
		return data[i * 8 + 2];
	}

	public float u(int i) {
		return data[i * 8 + 3];
	}

	public float v(int i) {
		return data[i * 8 + 4];
	}

	public float nx(int i) {
		return data[i * 8 + 5];
	}

	public float ny(int i) {
		return data[i * 8 + 6];
	}

	public float nz(int i) {
		return data[i * 8 + 7];
	}

	public int color(int i) {
		return colors[i];
	}

	void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz, int argb) {
		if (count == colors.length) {
			data = java.util.Arrays.copyOf(data, data.length * 2);
			colors = java.util.Arrays.copyOf(colors, colors.length * 2);
		}
		int o = count * 8;
		data[o] = x;
		data[o + 1] = y;
		data[o + 2] = z;
		data[o + 3] = u;
		data[o + 4] = v;
		data[o + 5] = nx;
		data[o + 6] = ny;
		data[o + 7] = nz;
		colors[count++] = argb;
	}
}
