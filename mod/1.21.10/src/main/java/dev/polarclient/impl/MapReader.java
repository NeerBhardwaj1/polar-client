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
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

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
		if (chunk == null) return false;
		int minY = level.getMinY(), maxY = level.getMaxY();
		boolean cave = scanFromY != Integer.MIN_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int x0 = cx << 4, z0 = cz << 4;
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int i = lz * 16 + lx;
				int y = cave ? Math.min(scanFromY, maxY) : chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
				int floor = cave ? Math.max(minY, y - CAVE_DEPTH) : minY;
				BlockState state = chunk.getBlockState(pos.set(x0 + lx, y, z0 + lz));
				MapColor color = state.getMapColor(level, pos);
				while (color == MapColor.NONE && y > floor) {
					state = chunk.getBlockState(pos.set(x0 + lx, --y, z0 + lz));
					color = state.getMapColor(level, pos);
				}
				rgb[i] = color == MapColor.NONE ? 0 : color.col;
				height[i] = y;
				int depth = 0;
				if (color == MapColor.WATER) {
					depth = 1;
					while (depth < MAX_WATER_DEPTH && y - depth > minY && !chunk.getBlockState(pos.set(x0 + lx, y - depth, z0 + lz)).getFluidState().isEmpty()) depth++;
				}
				water[i] = depth;
			}
		}
		return true;
	}

	static Object createTexture(String name, int width, int height) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Polar.MOD_ID, "dynamic/" + name + "_" + ++textures);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Polar " + name, width, height, true));
		return id;
	}

	static void upload(Object texture, int[] argb) {
		if (!(texture instanceof ResourceLocation id)) return;
		AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(id);
		if (!(t instanceof DynamicTexture dynamic) || dynamic.getPixels() == null) return;
		NativeImage image = dynamic.getPixels();
		int w = image.getWidth(), h = image.getHeight();
		if (argb.length < w * h) return;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) image.setPixel(x, y, argb[y * w + x]);
		}
		dynamic.upload();
	}

	static void destroy(Object texture) {
		if (texture instanceof ResourceLocation id) Minecraft.getInstance().getTextureManager().release(id);
	}
}
