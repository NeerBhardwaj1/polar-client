package dev.polarclient.modules.hud;

import dev.polarclient.module.Category;
import dev.polarclient.module.TextHudModule;

import java.util.List;

/** Your chunk and where you are inside it. */
public class ChunkModule extends TextHudModule {
	public ChunkModule() {
		super("chunk_coordinates", "Chunk Coordinates", "Show the chunk you're in.", Category.HUD, false, 4, 144);
	}

	@Override
	protected List<String> lines(boolean preview) {
		int x = (int) Math.floor(preview && !p().inWorld() ? 128 : p().playerX());
		int z = (int) Math.floor(preview && !p().inWorld() ? -301 : p().playerZ());
		return one("Chunk: " + (x >> 4) + ", " + (z >> 4) + "  (" + (x & 15) + ", " + (z & 15) + ")");
	}
}
