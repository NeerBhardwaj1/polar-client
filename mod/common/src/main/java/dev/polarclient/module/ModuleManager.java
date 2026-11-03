package dev.polarclient.module;

import dev.polarclient.modules.chat.ChatBackgroundModule;
import dev.polarclient.modules.chat.ChatFilterModule;
import dev.polarclient.modules.chat.ChatTimestampsModule;
import dev.polarclient.modules.chat.CompactChatModule;
import dev.polarclient.modules.chat.CopyChatModule;
import dev.polarclient.modules.hud.BossBarModule;
import dev.polarclient.modules.hud.ScoreboardModule;
import dev.polarclient.modules.hud.TabListModule;
import dev.polarclient.modules.inventory.InventorySearchModule;
import dev.polarclient.modules.inventory.ItemDurabilityModule;
import dev.polarclient.modules.inventory.ShulkerPreviewModule;
import dev.polarclient.modules.visual.ClearWaterModule;
import dev.polarclient.modules.visual.GlintColorModule;
import dev.polarclient.modules.visual.HitColorModule;
import dev.polarclient.modules.visual.HitboxesModule;
import dev.polarclient.modules.visual.LowFireModule;
import dev.polarclient.modules.visual.ParticlesModule;
import dev.polarclient.modules.visual.TimeWeatherModule;
import dev.polarclient.modules.world.CopyCoordsModule;
import dev.polarclient.modules.world.MinimapModule;
import dev.polarclient.modules.world.WaypointsModule;
import dev.polarclient.modules.AutoGGModule;
import dev.polarclient.modules.CustomFontModule;
import dev.polarclient.modules.EntityShadowsModule;
import dev.polarclient.modules.HitIndicatorModule;
import dev.polarclient.modules.HitSoundModule;
import dev.polarclient.modules.ScreenShakeModule;
import dev.polarclient.modules.SensitivityProfilesModule;
import dev.polarclient.modules.SmoothCameraModule;
import dev.polarclient.modules.hud.AttackCooldownModule;
import dev.polarclient.modules.hud.BiomeModule;
import dev.polarclient.modules.hud.ChunkModule;
import dev.polarclient.modules.hud.CooldownsModule;
import dev.polarclient.modules.hud.DeathCounterModule;
import dev.polarclient.modules.hud.DirectionModule;
import dev.polarclient.modules.hud.DurabilityWarningModule;
import dev.polarclient.modules.hud.EntityCounterModule;
import dev.polarclient.modules.hud.HealthModule;
import dev.polarclient.modules.hud.HeldItemModule;
import dev.polarclient.modules.hud.KillCounterModule;
import dev.polarclient.modules.hud.PackDisplayModule;
import dev.polarclient.modules.hud.SaturationModule;
import dev.polarclient.modules.hud.TotemCounterModule;
import dev.polarclient.modules.perf.CpuModule;
import dev.polarclient.modules.perf.FpsLimiterModule;
import dev.polarclient.modules.perf.FrameTimeModule;
import dev.polarclient.modules.perf.HideFarEntitiesModule;
import dev.polarclient.modules.perf.StutterMonitorModule;
import dev.polarclient.modules.social.DiscordStatusModule;
import dev.polarclient.modules.social.MentionAlertsModule;
import dev.polarclient.modules.world.DeathWaypointModule;
import dev.polarclient.modules.world.DistanceModule;
import dev.polarclient.modules.world.JourneyModule;
import dev.polarclient.modules.world.WorldBorderModule;
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
	private final List<Module> modules = new ArrayList<Module>();
	private final List<HudModule> hud = new ArrayList<HudModule>();

	public final ClientSettingsModule client = add(new ClientSettingsModule());
	public final ZoomModule zoom = add(new ZoomModule());
	public final FreelookModule freelook = add(new FreelookModule());
	public final FullbrightModule fullbright = add(new FullbrightModule());
	public final CustomCrosshairModule crosshair = add(new CustomCrosshairModule());
	public final HudTweaksModule hudTweaks = add(new HudTweaksModule());
	public final AutoGGModule autoGG = add(new AutoGGModule());
	public final LowHealthWarningModule lowHealth = add(new LowHealthWarningModule());
	public final NoHurtCamModule noHurtCam = add(new NoHurtCamModule());
	public final CustomFontModule customFont;
	// chat
	public final ChatTimestampsModule chatTimestamps;
	public final ChatFilterModule chatFilter;
	public final CopyChatModule copyChat;
	public final ChatBackgroundModule chatBackground;
	public final CompactChatModule compactChat;
	// visuals
	public final LowFireModule lowFire;
	public final ClearWaterModule clearWater;
	public final TimeWeatherModule timeWeather;
	public final ParticlesModule particles;
	public final HitColorModule hitColor;
	public final GlintColorModule glintColor;
	public final HitboxesModule hitboxes;
	public final dev.polarclient.cosmetics.PolarCosmeticsModule cosmetics;
	public final HideFarEntitiesModule hideFarEntities;
	// inventory
	public final ItemDurabilityModule itemDurability;
	public final ShulkerPreviewModule shulkerPreview;
	public final InventorySearchModule inventorySearch;
	// HUD replacements
	public final ScoreboardModule scoreboard;
	public final BossBarModule bossBar;
	public final TabListModule tabList;
	// world
	public final WaypointsModule waypoints;
	public final CopyCoordsModule copyCoords;
	public final MinimapModule minimap;

	public ModuleManager() {
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
		// HUD & information
		add(new BiomeModule());
		add(new DirectionModule());
		add(new ChunkModule());
		add(new EntityCounterModule());
		add(new HeldItemModule());
		add(new HealthModule());
		add(new SaturationModule());
		add(new DurabilityWarningModule());
		add(new PackDisplayModule());
		scoreboard = add(new ScoreboardModule());
		bossBar = add(new BossBarModule());
		tabList = add(new TabListModule());
		// visual
		add(new SmoothCameraModule());
		add(new EntityShadowsModule());
		lowFire = add(new LowFireModule());
		clearWater = add(new ClearWaterModule());
		timeWeather = add(new TimeWeatherModule());
		particles = add(new ParticlesModule());
		hitColor = add(new HitColorModule());
		glintColor = add(new GlintColorModule());
		hitboxes = add(new HitboxesModule());
		cosmetics = add(new dev.polarclient.cosmetics.PolarCosmeticsModule());
		// combat
		add(new HitIndicatorModule());
		add(new HitSoundModule());
		add(new AttackCooldownModule());
		add(new CooldownsModule());
		add(new TotemCounterModule());
		add(new KillCounterModule());
		add(new DeathCounterModule());
		// world
		add(new DeathWaypointModule());
		add(new JourneyModule());
		add(new WorldBorderModule());
		add(new DistanceModule());
		waypoints = add(new WaypointsModule());
		copyCoords = add(new CopyCoordsModule());
		minimap = add(new MinimapModule());
		// social
		add(new MentionAlertsModule());
		add(new DiscordStatusModule());
		chatTimestamps = add(new ChatTimestampsModule());
		chatFilter = add(new ChatFilterModule());
		copyChat = add(new CopyChatModule());
		chatBackground = add(new ChatBackgroundModule());
		compactChat = add(new CompactChatModule());
		// inventory
		itemDurability = add(new ItemDurabilityModule());
		shulkerPreview = add(new ShulkerPreviewModule());
		inventorySearch = add(new InventorySearchModule());
		// performance
		add(new FpsLimiterModule());
		add(new FrameTimeModule());
		add(new CpuModule());
		add(new StutterMonitorModule());
		hideFarEntities = add(new HideFarEntitiesModule());
		// accessibility
		add(new ScreenShakeModule());
		add(new SensitivityProfilesModule());
		// customization
		customFont = add(new CustomFontModule());
	}

	private <M extends Module> M add(M module) {
		modules.add(module);
		if (module instanceof HudModule) hud.add((HudModule) module);
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
