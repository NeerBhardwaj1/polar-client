package dev.polarclient.impl;

import com.mojang.math.Matrix4f;
import com.mojang.math.Vector4f;

/** The camera of the last drawn frame, for placing world positions (waypoints) on screen. */
public final class Render3d {
	private static Matrix4f viewProjection;
	private static double camX, camY, camZ;

	private Render3d() {
	}

	public static void capture(Matrix4f projection, Matrix4f view, double x, double y, double z) {
		Matrix4f m = projection.copy();
		m.multiply(view);
		viewProjection = m;
		camX = x;
		camY = y;
		camZ = z;
	}

	/** {x, y, depth} in GUI units for a {@code guiW} x {@code guiH} screen, or null when behind the camera. */
	public static float[] project(double x, double y, double z, int guiW, int guiH) {
		if (viewProjection == null) return null;
		Vector4f v = new Vector4f((float) (x - camX), (float) (y - camY), (float) (z - camZ), 1f);
		v.transform(viewProjection);
		if (v.w() < 0.05f) return null;
		float nx = Math.max(-40f, Math.min(40f, v.x() / v.w())), ny = Math.max(-40f, Math.min(40f, v.y() / v.w()));
		return new float[] {(nx * 0.5f + 0.5f) * guiW, (0.5f - ny * 0.5f) * guiH, v.w()};
	}
}
