package dev.polarclient.module;

import dev.polarclient.modules.AutoGGModule;
import dev.polarclient.modules.ClientSettingsModule;
import dev.polarclient.modules.CustomCrosshairModule;
import dev.polarclient.modules.FreelookModule;
import dev.polarclient.modules.FullbrightModule;
import dev.polarclient.modules.HudTweaksModule;
import dev.polarclient.modules.NoHurtCamModule;
import dev.polarclient.modules.ZoomModule;
import dev.polarclient.modules.hud.ArmorStatusModule;
import dev.polarclient.modules.hud.ClockModule;
import dev.polarclient.modules.hud.ComboCounterModule;
import dev.polarclient.modules.hud.CompassModule;
import dev.polarclient.modules.hud.CoordinatesModule;
import dev.polarclient.modules.hud.CpsModule;
import dev.polarclient.modules.hud.FpsModule;
import dev.polarclient.modules.hud.ItemCounterModule;
import dev.polarclient.modules.hud.KeystrokesModule;
import dev.polarclient.modules.hud.LowHealthWarningModule;
import dev.polarclient.modules.hud.MemoryModule;
import dev.polarclient.modules.hud.PingModule;
import dev.polarclient.modules.hud.PotionEffectsModule;
import dev.polarclient.modules.hud.ReachDisplayModule;
import dev.polarclient.modules.hud.ServerAddressModule;
import dev.polarclient.modules.hud.SessionTimeModule;
import dev.polarclient.modules.hud.SpeedModule;
import dev.polarclient.modules.hud.ToggleSprintModule;
import dev.polarclient.modules.hud.WatermarkModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Holds every module. Frequently used ones have direct fields for the mixins and renderer. */
public final class ModuleManager {
	private final List<Module> modules = new ArrayList<>();
	private final List<HudModule> hud = new ArrayList<>();

	public final ClientSettingsModule client = add(new ClientSettingsModule());
	public final ZoomModule zoom = add(new ZoomModule());
	public final FreelookModule freelook = add(new FreelookModule());
	public final FullbrightModule fullbright = add(new FullbrightModule());
	public final CustomCrosshairModule crosshair = add(new CustomCrosshairModule());
	public final HudTweaksModule hudTweaks = add(new HudTweaksModule());
	public final AutoGGModule autoGG = add(new AutoGGModule());
	public final LowHealthWarningModule lowHealth = add(new LowHealthWarningModule());

	public ModuleManager() {
		add(new NoHurtCamModule());
		add(new WatermarkModule());
		add(new FpsModule());
		add(new CpsModule());
		add(new PingModule());
		add(new KeystrokesModule());
		add(new ArmorStatusModule());
		add(new PotionEffectsModule());
		add(new ToggleSprintModule());
		add(new CoordinatesModule());
		add(new CompassModule());
		add(new ClockModule());
		add(new ServerAddressModule());
		add(new SpeedModule());
		add(new ReachDisplayModule());
		add(new ComboCounterModule());
		add(new ItemCounterModule());
		add(new SessionTimeModule());
		add(new MemoryModule());
	}

	private <M extends Module> M add(M module) {
		modules.add(module);
		if (module instanceof HudModule h) hud.add(h);
		return module;
	}

	public List<Module> all() {
		return Collections.unmodifiableList(modules);
	}

	public List<HudModule> hud() {
		return Collections.unmodifiableList(hud);
	}

	public Module byId(String id) {
		for (Module m : modules) if (m.id().equals(id)) return m;
		return null;
	}
}
