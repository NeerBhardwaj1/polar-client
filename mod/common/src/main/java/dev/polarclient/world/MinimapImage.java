package dev.polarclient.world;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The minimap's picture: map colours of the loaded chunks around you, read a few chunks per tick and kept per chunk,
 * then shaded like a vanilla map (lighter where the ground rises to the north, darker where it falls, deeper water darker).
 */
public final class MinimapImage {
	/** Reads one chunk; see Platform#mapChunk. */
	public interface Source {
		boolean read(int chunkX, int chunkZ, int scanFromY, int[] rgb, int[] height, int[] water);
	}

	static final int READS_PER_TICK = 40, REFRESHES_PER_TICK = 6, MAX_TRIES_PER_TICK = 800;
	static final long REFRESH_MS = 3000;

	static final class Chunk {
		final int[] rgb = new int[256], height = new int[256], water = new int[256];
		int scanY;
		long readAt;
	}

	private final Map<Long, Chunk> chunks = new HashMap<Long, Chunk>();
	private String worldKey;
	private int[] pixels = new int[0];
	private int size;
	/** The block at the picture's top-left texel. */
	private int originX, originZ;

	private static long key(int cx, int cz) {
		return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
	}

	public void clear() {
		chunks.clear();
		worldKey = null;
	}

	int cached() {
		return chunks.size();
	}

	/**
	 * Reads chunks within {@code radius} blocks of (x, z) that are missing or read at another height, nearest first,
	 * then re-reads a few older ones so changes show up. Returns true when anything new was read.
	 */
	public boolean update(Source source, String world, int x, int z, int radius, int scanY, long now) {
		if (world == null || !world.equals(worldKey)) {
			chunks.clear();
			worldKey = world;
		}
		int pcx = x >> 4, pcz = z >> 4, r = (radius >> 4) + 1;
		int reads = 0, refreshes = 0, tries = 0;
		boolean changed = false;
		for (int ring = 0; ring <= r; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
					int cx = pcx + dx, cz = pcz + dz;
					Chunk c = chunks.get(key(cx, cz));
					boolean missing = c == null || c.scanY != scanY;
					boolean stale = !missing && now - c.readAt > REFRESH_MS;
					if (missing ? reads >= READS_PER_TICK : !stale || refreshes >= REFRESHES_PER_TICK) continue;
					if (++tries > MAX_TRIES_PER_TICK) return changed;
					Chunk fresh = c != null ? c : new Chunk();
					if (source.read(cx, cz, scanY, fresh.rgb, fresh.height, fresh.water)) {
						fresh.scanY = scanY;
						fresh.readAt = now;
						chunks.put(key(cx, cz), fresh);
						changed = true;
						if (missing) reads++;
						else refreshes++;
					} else if (c != null) {
						chunks.remove(key(cx, cz)); // unloaded since
						changed = true;
					}
				}
			}
		}
		// forget what's well out of range
		for (Iterator<Map.Entry<Long, Chunk>> it = chunks.entrySet().iterator(); it.hasNext(); ) {
			long k = it.next().getKey();
			int cx = (int) (k >> 32), cz = (int) k;
			if (Math.abs(cx - pcx) > r + 2 || Math.abs(cz - pcz) > r + 2) {
				it.remove();
				changed = true;
			}
		}
		return changed;
	}

	/** Paints the size x size picture with (centerX, centerZ) in the middle. Unknown ground is transparent. */
	public void build(int size, int centerX, int centerZ) {
		if (pixels.length != size * size) pixels = new int[size * size];
		this.size = size;
		originX = centerX - size / 2;
		originZ = centerZ - size / 2;
		Chunk c = null;
		long ck = Long.MIN_VALUE;
		for (int j = 0; j < size; j++) {
			int bz = originZ + j;
			for (int i = 0; i < size; i++) {
				int bx = originX + i;
				long k = key(bx >> 4, bz >> 4);
				if (k != ck) {
					ck = k;
					c = chunks.get(k);
				}
				pixels[j * size + i] = c == null ? 0 : shade(c, bx, bz);
			}
		}
	}

	private int shade(Chunk c, int bx, int bz) {
		int idx = (bz & 15) * 16 + (bx & 15);
		int rgb = c.rgb[idx];
		if (rgb == 0) return 0;
		int checker = (bx + bz) & 1;
		double d;
		int light;
		if (c.water[idx] > 0) {
			d = c.water[idx] * 0.1 + checker * 0.2;
			light = d < 0.5 ? 255 : d > 0.9 ? 180 : 220;
		} else {
			int north = heightAt(c, bx, bz - 1, c.height[idx]);
			d = (c.height[idx] - north) * 0.8 + (checker - 0.5) * 0.4;
			light = d > 0.6 ? 255 : d < -0.6 ? 180 : 220;
		}
		int r = (rgb >> 16 & 255) * light / 255, g = (rgb >> 8 & 255) * light / 255, b = (rgb & 255) * light / 255;
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	private int heightAt(Chunk inside, int bx, int bz, int fallback) {
		Chunk c = (bz >> 4) == (bz + 1 >> 4) ? inside : chunks.get(key(bx >> 4, bz >> 4));
		return c == null ? fallback : c.height[(bz & 15) * 16 + (bx & 15)];
	}

	public int[] pixels() {
		return pixels;
	}

	public int size() {
		return size;
	}

	public int originX() {
		return originX;
	}

	public int originZ() {
		return originZ;
	}
}
