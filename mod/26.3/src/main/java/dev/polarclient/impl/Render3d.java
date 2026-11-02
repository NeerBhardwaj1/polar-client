package dev.polarclient.impl;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/** The camera of the last drawn frame, for placing world positions (waypoints) on screen. */
public final class Render3d {
	private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
	private static double camX, camY, camZ;
	private static boolean ready;

	private Render3d() {
	}

	public static void capture(Matrix4fc projection, Matrix4fc viewRotation, double x, double y, double z) {
		VIEW_PROJECTION.set(projection).mul(viewRotation);
		camX = x;
		camY = y;
		camZ = z;
		ready = true;
	}

	public static void reset() {
		ready = false;
	}

	/** {x, y, depth} in GUI units for a {@code guiW} x {@code guiH} screen, or null when behind the camera. */
	public static float[] project(double x, double y, double z, int guiW, int guiH) {
		if (!ready) return null;
		Vector4f v = new Vector4f((float) (x - camX), (float) (y - camY), (float) (z - camZ), 1f);
		VIEW_PROJECTION.transform(v);
		if (v.w < 0.05f) return null;
		float nx = Math.max(-40f, Math.min(40f, v.x / v.w)), ny = Math.max(-40f, Math.min(40f, v.y / v.w));
		return new float[] {(nx * 0.5f + 0.5f) * guiW, (0.5f - ny * 0.5f) * guiH, v.w};
	}
}
