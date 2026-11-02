package dev.polarclient.modules.hud;

import dev.polarclient.module.TextHudModule;

import java.util.List;

public class MemoryModule extends TextHudModule {
	public MemoryModule() {
		super("memory", "Memory", "Java memory in use.", false, 4, 210);
	}

	@Override
	protected List<String> lines(boolean preview) {
		Runtime rt = Runtime.getRuntime();
		long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
		long max = rt.maxMemory() / 1048576L;
		return List.of("Mem: " + (used * 100 / Math.max(1, max)) + "% " + used + "/" + max + "MB");
	}
}
