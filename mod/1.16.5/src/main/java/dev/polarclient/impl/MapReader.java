package dev.polarclient.impl;

import com.mojang.blaze3d.platform.NativeImage;
import dev.polarclient.Polar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MaterialColor;

/** The minimap's reads from loaded chunks and its texture. */
final class MapReader {
	/** How far down the Nether view looks for a floor before giving up. */
	private static final int CAVE_DEPTH = 64, MAX_WATER_DEPTH = 16;
	private static int textures;

	private MapReader() {
	}

	static boolean read(int cx, int cz, int scanFromY, int[] rgb, int[] height, int[] water) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) return false;
		LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
		if (chunk == null || chunk.isEmpty()) return false;
		boolean cave = scanFromY != Integer.MIN_VALUE;
		int maxY = level.getMaxBuildHeight() - 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int x0 = cx << 4, z0 = cz << 4;
		try {
			for (int lz = 0; lz < 16; lz++) {
				for (int lx = 0; lx < 16; lx++) {
					int i = lz * 16 + lx;
					int y = cave ? Math.min(scanFromY, maxY) : chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
					int floor = cave ? Math.max(0, y - CAVE_DEPTH) : 0;
					BlockState state = chunk.getBlockState(pos.set(x0 + lx, y, z0 + lz));
					MaterialColor color = state.getMapColor(level, pos);
					while (color == MaterialColor.NONE && y > floor) {
						state = chunk.getBlockState(pos.set(x0 + lx, --y, z0 + lz));
						color = state.getMapColor(level, pos);
					}
					rgb[i] = color == MaterialColor.NONE ? 0 : color.col;
					height[i] = y;
					int depth = 0;
					if (color == MaterialColor.WATER) {
						depth = 1;
						while (depth < MAX_WATER_DEPTH && y - depth > 0 && !chunk.getBlockState(pos.set(x0 + lx, y - depth, z0 + lz)).getFluidState().isEmpty()) depth++;
					}
					water[i] = depth;
				}
			}
		} catch (RuntimeException e) {
			return false; // a chunk still being filled in (no heightmap yet)
		}
		return true;
	}

	static Object createTexture(String name, int width, int height) {
		ResourceLocation id = new ResourceLocation(Polar.MOD_ID, "dynamic/" + name + "_" + ++textures);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(width, height, true));
		return id;
	}

	static void upload(Object texture, int[] argb) {
		if (!(texture instanceof ResourceLocation)) return;
		AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture((ResourceLocation) texture);
		if (!(t instanceof DynamicTexture) || ((DynamicTexture) t).getPixels() == null) return;
		DynamicTexture dynamic = (DynamicTexture) t;
		NativeImage image = dynamic.getPixels();
		int w = image.getWidth(), h = image.getHeight();
		if (argb.length < w * h) return;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = argb[y * w + x];
				// NativeImage wants ABGR
				image.setPixelRGBA(x, y, (c & 0xFF00FF00) | (c >> 16 & 0xFF) | (c & 0xFF) << 16);
			}
		}
		dynamic.upload();
	}

	static void destroy(Object texture) {
		if (texture instanceof ResourceLocation) Minecraft.getInstance().getTextureManager().release((ResourceLocation) texture);
	}
}
