package dev.polarclient.render;

import dev.polarclient.Polar;
import dev.polarclient.modules.visual.MotionBlurModule;

import java.lang.reflect.Method;

/**
 * The maths behind Motion Blur, shared by every version. Each frame the version code passes the camera's yaw, pitch
 * and lens; this works out how fast the camera is turning and fills in the numbers the blur shader needs.
 *
 * <ul>
 * <li>Turn speed (degrees a second) is smoothed over about 25 ms, so a mouse that reports less often than the game
 * draws frames doesn't make the blur flicker, and the blur is the same at 60 or 240 FPS.</li>
 * <li>The blur covers the camera's turn during a short "shutter" ({@link MotionBlurModule#exposureSeconds}); each
 * pixel is blurred along the path it moves on screen for that turn.</li>
 * <li>Only turning blurs: walking, view bobbing, hurt shake and zooming don't, and nothing is drawn while the camera
 * is still. A jump of the view (teleport, F5, respawn) starts again from still.</li>
 * </ul>
 *
 * The shader numbers ({@link #params}): 0-8 a 3x3 rotation, column by column, that turns a view direction now into
 * where it pointed at the start of the shutter (camera space: x right, y up, looking down -z); 9 and 10 the tangents
 * of half the horizontal and vertical field of view (negative where the picture is drawn flipped); 11 the longest
 * blur, as a fraction of the screen.
 */
public final class CameraBlur {
	public static final int PARAMS = 16;
	/** How quickly the measured turn speed follows the mouse (seconds). */
	private static final float SMOOTHING = 0.025f;
	/** A turn bigger than this between two frames is a jump of the view, not a turn. */
	private static final float CUT_DEGREES = 60f;
	/** After this long without a frame (menus, loading), start again from still. */
	private static final float GAP_SECONDS = 0.25f;
	/** The longest blur, as a fraction of the screen's width or height. */
	public static final float MAX_LENGTH = 0.06f;
	/** Below this much movement in the middle of the screen (pixels), there's nothing worth blurring. */
	private static final float MIN_PIXELS = 0.35f;

	private static final float[] PARAMS_OUT = new float[PARAMS];
	private static long lastNanos;
	private static float lastYaw, lastPitch;
	private static float yawSpeed, pitchSpeed;
	private static boolean started;

	private CameraBlur() {
	}

	/**
	 * The module, or null when it's off (or broken on this graphics card), or while an Iris shader pack is on: packs
	 * draw the world (and the hand) their own way and usually have their own motion blur.
	 */
	public static MotionBlurModule active() {
		MotionBlurModule m = Polar.modules() == null ? null : Polar.modules().motionBlur;
		if (m == null || !m.active() || shaderPackInUse()) {
			started = false;
			return null;
		}
		return m;
	}

	private static boolean irisLooked;
	private static Object iris;
	private static Method irisPackInUse;

	/** Whether Iris (if installed) is drawing with a shader pack. Asked through Iris's public API, by name. */
	private static boolean shaderPackInUse() {
		if (!irisLooked) {
			irisLooked = true;
			try {
				Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, CameraBlur.class.getClassLoader());
				iris = api.getMethod("getInstance").invoke(null);
				irisPackInUse = api.getMethod("isShaderPackInUse");
			} catch (Throwable notThere) {
				irisPackInUse = null;
			}
		}
		if (irisPackInUse == null) return false;
		try {
			return Boolean.TRUE.equals(irisPackInUse.invoke(iris));
		} catch (Throwable t) {
			irisPackInUse = null;
			return false;
		}
	}

	/**
	 * Works out this frame's blur. yaw and pitch: the camera's, in degrees, as the game uses them (pitch positive =
	 * looking down). tanX, tanY: tangents of half the horizontal and vertical field of view. screenHeight: in pixels.
	 * Returns true when there is something to blur; {@link #params} then holds the shader's numbers.
	 */
	public static boolean update(MotionBlurModule m, float yaw, float pitch, float tanX, float tanY, int screenHeight) {
		long now = System.nanoTime();
		float dt = (now - lastNanos) / 1e9f;
		float dYaw = wrap(yaw - lastYaw);
		float dPitch = pitch - lastPitch;
		boolean cut = !started || dt > GAP_SECONDS || dt <= 0 || Math.abs(dYaw) > CUT_DEGREES || Math.abs(dPitch) > CUT_DEGREES;
		lastNanos = now;
		lastYaw = yaw;
		lastPitch = pitch;
		started = true;
		if (cut) {
			yawSpeed = 0;
			pitchSpeed = 0;
			return false;
		}
		// follow the measured turn speed smoothly (the same smoothing whatever the frame rate)
		float follow = 1f - (float) Math.exp(-dt / SMOOTHING);
		yawSpeed += (dYaw / dt - yawSpeed) * follow;
		pitchSpeed += (dPitch / dt - pitchSpeed) * follow;

		float shutter = m.exposureSeconds();
		float turnYaw = yawSpeed * shutter, turnPitch = pitchSpeed * shutter;
		// roughly how far the middle of the screen moves, in pixels (small angles)
		double turn = Math.toRadians(Math.sqrt(turnYaw * turnYaw + turnPitch * turnPitch));
		float lens = Math.abs(tanY);
		if (lens < 1e-4f || turn / lens * screenHeight * 0.5 < MIN_PIXELS) return false;

		// camera rotation (camera space to world) is Ry(180 - yaw) * Rx(-pitch); the turn between the start of the
		// shutter and now, in today's camera space, is Rx(pitchThen) * Ry(yawThen - yawNow) * Rx(-pitchNow)
		double pitchNow = Math.toRadians(pitch);
		double pitchThen = Math.toRadians(pitch - turnPitch);
		double yawStep = Math.toRadians(-turnYaw);
		double[][] r = mul(mul(rotX(pitchThen), rotY(yawStep)), rotX(-pitchNow));
		float[] p = PARAMS_OUT;
		for (int col = 0; col < 3; col++) {
			for (int row = 0; row < 3; row++) p[col * 3 + row] = (float) r[row][col];
		}
		p[9] = tanX;
		p[10] = tanY;
		p[11] = MAX_LENGTH;
		for (int i = 12; i < PARAMS; i++) p[i] = 0;
		return true;
	}

	/** The shader's numbers from the last {@link #update} that returned true. */
	public static float[] params() {
		return PARAMS_OUT;
	}

	private static float wrap(float degrees) {
		degrees %= 360f;
		if (degrees >= 180f) degrees -= 360f;
		if (degrees < -180f) degrees += 360f;
		return degrees;
	}

	private static double[][] rotX(double a) {
		double c = Math.cos(a), s = Math.sin(a);
		return new double[][] {{1, 0, 0}, {0, c, -s}, {0, s, c}};
	}

	private static double[][] rotY(double a) {
		double c = Math.cos(a), s = Math.sin(a);
		return new double[][] {{c, 0, s}, {0, 1, 0}, {-s, 0, c}};
	}

	private static double[][] mul(double[][] a, double[][] b) {
		double[][] o = new double[3][3];
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) o[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
		}
		return o;
	}
}
