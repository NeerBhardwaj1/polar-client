package dev.polarclient.impl;

import dev.polarclient.Polar;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.MaterialColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.Texture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

/** The minimap's reads from loaded chunks and its texture. */
final class MapReader {
	/** How far down the Nether view looks for a floor before giving up. */
	private static final int CAVE_DEPTH = 64, MAX_WATER_DEPTH = 16, MAX_Y = 255;
	private static int textures;

	private MapReader() {
	}

	private static MaterialColor color(BlockState state) {
		return state.getBlock().getMaterialColor(state);
	}

	static boolean read(int cx, int cz, int scanFromY, int[] rgb, int[] height, int[] water) {
		ClientWorld world = MinecraftClient.getInstance().world;
		if (world == null) return false;
		Chunk chunk = world.getChunk(cx, cz);
		if (chunk == null || chunk.isEmpty()) return false;
		boolean cave = scanFromY != Integer.MIN_VALUE;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int x0 = cx << 4, z0 = cz << 4;
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int i = lz * 16 + lx;
				// the light heightmap sits just above the top block that stops light
				int y = cave ? Math.min(scanFromY, MAX_Y) : Math.min(chunk.getHighestBlockY(lx, lz), MAX_Y);
				int floor = cave ? Math.max(0, y - CAVE_DEPTH) : 0;
				BlockState state = chunk.method_9154(pos.setPosition(x0 + lx, y, z0 + lz));
				MaterialColor color = color(state);
				while (color == MaterialColor.AIR && y > floor) {
					state = chunk.method_9154(pos.setPosition(x0 + lx, --y, z0 + lz));
					color = color(state);
				}
				rgb[i] = color == MaterialColor.AIR ? 0 : color.color;
				height[i] = y;
				int depth = 0;
				if (color == MaterialColor.WATER) {
					depth = 1;
					while (depth < MAX_WATER_DEPTH && y - depth > 0 && chunk.method_9154(pos.setPosition(x0 + lx, y - depth, z0 + lz)).getBlock().getMaterial().isFluid()) depth++;
				}
				water[i] = depth;
			}
		}
		return true;
	}

	static Object createTexture(String name, int width, int height) {
		Identifier id = new Identifier(Polar.MOD_ID, "dynamic/" + name + "_" + ++textures);
		MinecraftClient.getInstance().getTextureManager().loadTexture(id, new NativeImageBackedTexture(width, height));
		return id;
	}

	static void upload(Object texture, int[] argb) {
		if (!(texture instanceof Identifier)) return;
		Texture t = MinecraftClient.getInstance().getTextureManager().getTexture((Identifier) texture);
		if (!(t instanceof NativeImageBackedTexture)) return;
		NativeImageBackedTexture dynamic = (NativeImageBackedTexture) t;
		int[] pixels = dynamic.getPixels();
		System.arraycopy(argb, 0, pixels, 0, Math.min(argb.length, pixels.length));
		dynamic.upload();
	}

	static void destroy(Object texture) {
		if (texture instanceof Identifier) MinecraftClient.getInstance().getTextureManager().close((Identifier) texture);
	}
}
