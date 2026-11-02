package dev.polarclient.util;

/** Frame times, sampled each time the HUD is drawn (once per frame), for the Frame Time and Stutter Monitor mods. */
public final class FrameStats {
	public static final int SIZE = 120;
	private static final float[] MS = new float[SIZE];
	private static int head, count;
	private static long last;
	private static final long[] SPIKES = new long[256];
	private static int spikeHead;

	private FrameStats() {
	}

	public static void frame() {
		long now = System.nanoTime();
		if (last != 0) {
			float ms = (now - last) / 1_000_000f;
			if (ms < 2000) {
				float avg = average();
				MS[head] = ms;
				head = (head + 1) % SIZE;
				count = Math.min(SIZE, count + 1);
				if (count > 30 && ms > Math.max(50, avg * 2.5f)) SPIKES[spikeHead++ % SPIKES.length] = System.currentTimeMillis();
			}
		}
		last = now;
	}

	/** i = 0 is the oldest sample. */
	public static float sample(int i) {
		int n = count;
		return n == 0 ? 0 : MS[(head - n + i + SIZE * 2) % SIZE];
	}

	public static int count() {
		return count;
	}

	public static float latest() {
		return count == 0 ? 0 : MS[(head - 1 + SIZE) % SIZE];
	}

	public static float average() {
		if (count == 0) return 0;
		float s = 0;
		for (int i = 0; i < count; i++) s += sample(i);
		return s / count;
	}

	public static float max() {
		float m = 0;
		for (int i = 0; i < count; i++) m = Math.max(m, sample(i));
		return m;
	}

	/** Frame spikes (frames over 2.5x the average and over 50 ms) in the last {@code windowMs}. */
	public static int spikes(long windowMs) {
		long since = System.currentTimeMillis() - windowMs;
		int n = 0;
		for (long t : SPIKES) if (t >= since) n++;
		return n;
	}
}
