package dev.polarclient.impl;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

/** The camera of the last drawn frame (read from OpenGL), for placing world positions (waypoints) on screen. */
public final class Render3d {
	private static final FloatBuffer BUFFER = BufferUtils.createFloatBuffer(16);
	private static final float[] MODELVIEW = new float[16], PROJECTION = new float[16];
	private static double camX, camY, camZ;
	private static boolean ready;

	private Render3d() {
	}

	/** Call right after the camera is set up; x/y/z = where the world is drawn from (the camera entity's feet). */
	public static void capture(double x, double y, double z) {
		BUFFER.clear();
		GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, BUFFER);
		BUFFER.get(MODELVIEW);
		BUFFER.clear();
		GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, BUFFER);
		BUFFER.get(PROJECTION);
		camX = x;
		camY = y;
		camZ = z;
		ready = true;
	}

	private static float[] mul(float[] m, float[] v) {
		float[] out = new float[4];
		for (int i = 0; i < 4; i++) out[i] = m[i] * v[0] + m[4 + i] * v[1] + m[8 + i] * v[2] + m[12 + i] * v[3];
		return out;
	}

	/** {x, y, depth} in GUI units for a {@code guiW} x {@code guiH} screen, or null when behind the camera. */
	public static float[] project(double x, double y, double z, int guiW, int guiH) {
		if (!ready) return null;
		float[] v = mul(PROJECTION, mul(MODELVIEW, new float[] {(float) (x - camX), (float) (y - camY), (float) (z - camZ), 1f}));
		if (v[3] < 0.05f) return null;
		float nx = Math.max(-40f, Math.min(40f, v[0] / v[3])), ny = Math.max(-40f, Math.min(40f, v[1] / v[3]));
		return new float[] {(nx * 0.5f + 0.5f) * guiW, (0.5f - ny * 0.5f) * guiH, v[3]};
	}
}
