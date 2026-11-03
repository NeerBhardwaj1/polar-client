package dev.polarclient.impl;

import dev.polarclient.account.AccountSwitcher;
import dev.polarclient.account.LauncherBridge;
import dev.polarclient.gfx.UiFont;
import dev.polarclient.gui.View;
import dev.polarclient.impl.mixin.KeyBindingAccessor;
import dev.polarclient.impl.mixin.MinecraftClientAccessor;
import dev.polarclient.platform.EffectView;
import dev.polarclient.platform.GameKey;
import dev.polarclient.platform.ItemKind;
import dev.polarclient.platform.ItemView;
import dev.polarclient.platform.Platform;
import dev.polarclient.platform.PolarKey;
import dev.polarclient.platform.ServerBackend;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SettingsScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.options.SkinOptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.Session;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.realms.RealmsBridge;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.resource.ResourcePackLoader;
import org.lwjgl.opengl.Display;

/** The game, as Polar's shared code sees it, on Minecraft 1.8.9. */
public final class McPlatform extends Platform {
	private final Map<ItemKind, ItemView> kindIcons = new EnumMap<ItemKind, ItemView>(ItemKind.class);

	private static MinecraftClient mc() {
		return MinecraftClient.getInstance();
	}

	// ------------------------------------------------------------------ game & window

	@Override
	public String minecraftVersion() {
		return "1.8.9";
	}

	@Override
	public int fps() {
		return MinecraftClient.getCurrentFps();
	}

	@Override
	public int guiScale() {
		return new net.minecraft.client.util.Window(mc()).getScaleFactor();
	}

	@Override
	public void execute(Runnable task) {
		mc().submit(task);
	}

	@Override
	public void playClickSound() {
		mc().getSoundManager().play(PositionedSoundInstance.master(new Identifier("gui.button.press"), 1.0f));
	}

	@Override
	public void quit() {
		mc().scheduleStop();
	}

	@Override
	public boolean debugScreenOpen() {
		return mc().options.debugEnabled;
	}

	@Override
	public String clipboard() {
		return Screen.getClipboard();
	}

	@Override
	public void setClipboard(String text) {
		Screen.setClipboard(text);
	}

	// ------------------------------------------------------------------ text

	@Override
	public int textWidth(String s, UiFont font) {
		return font == UiFont.VANILLA ? mc().textRenderer.getStringWidth(s) : (int) Math.ceil(TtfFont.of(font).width(s));
	}

	@Override
	public float exactTextWidth(String s, UiFont font) {
		return font == UiFont.VANILLA ? mc().textRenderer.getStringWidth(s) : TtfFont.of(font).width(s);
	}

	@Override
	public int vanillaLineHeight() {
		return mc().textRenderer.fontHeight;
	}

	@Override
	public String trimToWidth(String s, int maxWidth) {
		return mc().textRenderer.trimToWidth(s, maxWidth);
	}

	// ------------------------------------------------------------------ screens

	@Override
	public void open(View view) {
		mc().setScreen(new ViewScreen(view));
	}

	@Override
	public void show(Object screen) {
		mc().setScreen((Screen) screen);
	}

	@Override
	public Object currentScreen() {
		return mc().currentScreen;
	}

	@Override
	public View currentView() {
		return mc().currentScreen instanceof ViewScreen ? ((ViewScreen) mc().currentScreen).view : null;
	}

	@Override
	public boolean screenOpen() {
		return mc().currentScreen != null;
	}

	@Override
	public void openSingleplayer(Object parent) {
		mc().setScreen(new SelectWorldScreen((Screen) parent));
	}

	@Override
	public void openMultiplayer(Object parent) {
		// Polar's list replaces the vanilla one (MinecraftClientMixin) when it's turned on in Polar Settings
		mc().setScreen(new MultiplayerScreen((Screen) parent));
	}

	@Override
	public void openRealms(Object parent) {
		new RealmsBridge().switchToRealms((Screen) parent);
	}

	@Override
	public void openOptions(Object parent) {
		mc().setScreen(new SettingsScreen((Screen) parent, mc().options));
	}

	@Override
	public void openSkinOptions(Object parent) {
		mc().setScreen(new SkinOptionsScreen((Screen) parent));
	}

	@Override
	public ServerBackend createServerBackend(Object screenToReturnTo) {
		return new McServerBackend((Screen) screenToReturnTo);
	}

	@Override
	public boolean hideServerAddresses() {
		return mc().options.hideServerAddress;
	}

	// ------------------------------------------------------------------ world & player

	private static ClientPlayerEntity player() {
		return mc().player;
	}

	@Override
	public boolean inWorld() {
		return mc().world != null;
	}

	@Override
	public boolean hasPlayer() {
		return player() != null;
	}

	@Override
	public boolean isSingleplayer() {
		return mc().isIntegratedServerRunning();
	}

	@Override
	public String serverAddress() {
		ServerInfo server = mc().getCurrentServerEntry();
		return server == null || mc().isIntegratedServerRunning() ? null : server.address;
	}

	@Override
	public int ping() {
		ClientPlayNetworkHandler connection = mc().getNetworkHandler();
		if (connection == null || player() == null) return -1;
		PlayerListEntry info = connection.getPlayerListEntry(player().getUuid());
		return info == null ? -1 : info.getLatency();
	}

	@Override
	public double playerX() {
		return player().x;
	}

	@Override
	public double playerY() {
		return player().y;
	}

	@Override
	public double playerZ() {
		return player().z;
	}

	@Override
	public float playerYaw() {
		return player().yaw;
	}

	@Override
	public float playerPitch() {
		return player().pitch;
	}

	@Override
	public String facing() {
		return player().getHorizontalDirection().getName();
	}

	@Override
	public String biome() {
		if (mc().world == null || player() == null) return null;
		Biome biome = mc().world.getBiome(new BlockPos(player()));
		return biome == null || biome.name == null ? null : biome.name.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
	}

	@Override
	public double horizontalSpeed() {
		ClientPlayerEntity p = player();
		return Math.sqrt(p.velocityX * p.velocityX + p.velocityZ * p.velocityZ) * 20.0;
	}

	@Override
	public float health() {
		return player().getHealth();
	}

	@Override
	public boolean creativeOrSpectator() {
		return player().abilities.creativeMode || player().isSpectator();
	}

	@Override
	public boolean spectator() {
		return player() != null && player().isSpectator();
	}

	@Override
	public int hurtTime() {
		return player().hurtTime;
	}

	@Override
	public boolean sprinting() {
		return player().isSprinting();
	}

	public static ItemView view(ItemStack s) {
		return new ItemView(s, s.isDamageable(), s.getMaxDamage(), s.getDamage(), s.count);
	}

	@Override
	public List<ItemView> armorAndHand(boolean includeHand) {
		List<ItemView> out = new ArrayList<ItemView>();
		ClientPlayerEntity p = player();
		if (p == null) return out;
		// the armour array runs boots (0) to helmet (3)
		for (int slot = 3; slot >= 0; slot--) {
			ItemStack s = p.inventory.armor[slot];
			if (s != null) out.add(view(s));
		}
		ItemStack hand = p.inventory.getMainHandStack();
		if (includeHand && hand != null) out.add(view(hand));
		return out;
	}

	@Override
	public List<ItemView> previewArmor(boolean includeHand) {
		List<ItemView> out = new ArrayList<ItemView>();
		out.add(view(new ItemStack(Items.DIAMOND_HELMET)));
		out.add(view(new ItemStack(Items.DIAMOND_CHESTPLATE)));
		out.add(view(new ItemStack(Items.DIAMOND_LEGGINGS)));
		out.add(view(new ItemStack(Items.DIAMOND_BOOTS)));
		if (includeHand) out.add(view(new ItemStack(Items.DIAMOND_SWORD)));
		return out;
	}

	/** 1.8.9 has no totem of undying, so that counter stays at zero. */
	private static Item item(ItemKind kind) {
		switch (kind) {
			case TOTEM: return null;
			case ENDER_PEARL: return Items.ENDER_PEARL;
			case GOLDEN_APPLE: return Items.GOLDEN_APPLE;
			case SHIELD:
			case CHORUS_FRUIT: return null; // not in 1.8.9
			default: return Items.ARROW;
		}
	}

	@Override
	public int countItems(ItemKind kind) {
		ClientPlayerEntity p = player();
		Item target = item(kind);
		if (p == null || target == null) return 0;
		int n = 0;
		for (ItemStack s : p.inventory.main) {
			if (s != null && s.getItem() == target) n += s.count;
		}
		return n;
	}

	@Override
	public ItemView itemOf(ItemKind kind) {
		Item target = item(kind);
		if (target == null) return null;
		ItemView v = kindIcons.get(kind);
		if (v == null) kindIcons.put(kind, v = view(new ItemStack(target)));
		return v;
	}

	private static EffectView effect(StatusEffectInstance e) {
		StatusEffect type = StatusEffect.STATUS_EFFECTS[e.getEffectId()];
		String name = EffectView.withLevel(type == null ? "?" : I18n.translate(type.getTranslationKey()), e.getAmplifier());
		return new EffectView(e, name, StatusEffect.getFormattedDuration(e), e.getDuration() <= 200);
	}

	@Override
	public List<EffectView> effects(boolean hideAmbient) {
		List<EffectView> out = new ArrayList<EffectView>();
		ClientPlayerEntity p = player();
		if (p == null) return out;
		for (StatusEffectInstance e : p.getStatusEffectInstances()) {
			if (hideAmbient && e.isAmbient()) continue;
			out.add(effect(e));
		}
		return out;
	}

	@Override
	public List<EffectView> previewEffects() {
		List<EffectView> out = new ArrayList<EffectView>();
		out.add(effect(new StatusEffectInstance(StatusEffect.SPEED.id, 1800, 1)));
		out.add(effect(new StatusEffectInstance(StatusEffect.STRENGTH.id, 900, 0)));
		out.add(effect(new StatusEffectInstance(StatusEffect.FIRE_RESISTANCE.id, 3600, 0)));
		return out;
	}

	@Override
	public void sendChat(String message) {
		if (player() != null) player().sendChatMessage(message);
	}

	// ------------------------------------------------------------------ keys & options

	private static KeyBinding mapping(GameKey key) {
		GameOptions o = mc().options;
		switch (key) {
			case FORWARD: return o.forwardKey;
			case LEFT: return o.leftKey;
			case BACK: return o.backKey;
			case RIGHT: return o.rightKey;
			case ATTACK: return o.attackKey;
			case USE: return o.useKey;
			case JUMP: return o.jumpKey;
			case SNEAK: return o.sneakKey;
			default: return o.sprintKey;
		}
	}

	@Override
	public boolean keyDown(GameKey key) {
		return mapping(key).isPressed();
	}

	@Override
	public String keyLabel(GameKey key) {
		return GameOptions.getFormattedNameForKeyCode(mapping(key).getCode());
	}

	@Override
	public void setKeyDown(GameKey key, boolean down) {
		((KeyBindingAccessor) mapping(key)).polar$setPressed(down);
	}

	@Override
	public boolean physicalKeyDown(GameKey key) {
		int code = mapping(key).getCode();
		if (code == 0) return false;
		return code < 0 ? Mouse.isButtonDown(code + 100) : Keyboard.isKeyDown(code);
	}

	@Override
	public boolean polarKeyDown(PolarKey key) {
		return Keys.get(key).isPressed();
	}

	@Override
	public boolean consumePolarKey(PolarKey key) {
		return Keys.get(key).wasPressed();
	}

	@Override
	public int perspective() {
		return mc().options.perspective;
	}

	@Override
	public void setPerspective(int perspective) {
		mc().options.perspective = perspective;
	}

	@Override
	public boolean smoothCamera() {
		return mc().options.smoothCameraEnabled;
	}

	@Override
	public void setSmoothCamera(boolean on) {
		mc().options.smoothCameraEnabled = on;
	}

	@Override
	public boolean hasToggleOptions() {
		return false; // Toggle Sprint keeps its own toggles here
	}

	@Override
	public boolean toggleSprint() {
		return false;
	}

	@Override
	public void setToggleSprint(boolean on) {
	}

	@Override
	public boolean toggleSneak() {
		return false;
	}

	@Override
	public void setToggleSneak(boolean on) {
	}

	@Override
	public boolean hasDamageTiltOption() {
		return false; // GameRendererMixin scales the shake instead
	}

	@Override
	public double damageTilt() {
		return 1.0;
	}

	@Override
	public void setDamageTilt(double value) {
	}

	// ------------------------------------------------------------------ account

	@Override
	public String userName() {
		return mc().getSession().getUsername();
	}

	@Override
	public UUID userId() {
		return AccountSwitcher.parse(mc().getSession().getUuid());
	}

	/** Swaps the signed-in session; skins and properties are fetched again for the new account. */
	@Override
	public void switchSession(LauncherBridge.Session s) {
		UUID id = AccountSwitcher.parse(s.uuid());
		Session session = new Session(s.name(), id.toString().replace("-", ""), s.accessToken(), s.offline() ? "legacy" : "mojang");
		MinecraftClientAccessor access = (MinecraftClientAccessor) mc();
		access.polar$setSession(session);
		access.polar$sessionProperties().clear();
	}

	// ------------------------------------------------------------------ more player & world info (HUD mods)

	@Override
	public float maxHealth() {
		return player() == null ? 20f : player().getMaxHealth();
	}

	@Override
	public float absorption() {
		return player() == null ? 0f : player().getAbsorption();
	}

	@Override
	public int foodLevel() {
		return player() == null ? 20 : player().getHungerManager().getFoodLevel();
	}

	@Override
	public float saturation() {
		return player() == null ? 0f : player().getHungerManager().getSaturationLevel();
	}

	@Override
	public int entityCount() {
		return mc().world == null ? 0 : mc().world.loadedEntities.size();
	}

	@Override
	public ItemView heldItem() {
		if (player() == null) return null;
		ItemStack s = player().getMainHandStack();
		return s == null ? null : view(s);
	}

	@Override
	public String itemName(ItemView item) {
		return item != null && item.handle instanceof ItemStack ? ((ItemStack) item.handle).getCustomName() : "";
	}

	@Override
	public List<String> resourcePacks() {
		List<String> out = new ArrayList<String>();
		for (ResourcePackLoader.Entry e : mc().getResourcePackLoader().getSelectedResourcePacks()) {
			String name = e.getName();
			if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) name = name.substring(0, name.length() - 4);
			out.add(name);
		}
		return out;
	}

	@Override
	public boolean hasAttackCooldown() {
		return false;
	}

	@Override
	public float attackStrength() {
		return 1f;
	}

	@Override
	public float itemCooldown(ItemKind kind) {
		return 0f; // 1.8.9 has no item cooldowns
	}

	@Override
	public double worldBorderDistance() {
		if (mc().world == null || player() == null) return -1;
		return mc().world.getWorldBorder().getDistanceInsideBorder(player());
	}

	@Override
	public String dimension() {
		if (player() == null) return "";
		return player().dimension == -1 ? "the_nether" : player().dimension == 1 ? "the_end" : "overworld";
	}

	@Override
	public int maxFps() {
		return mc().options.maxFramerate;
	}

	@Override
	public void setMaxFps(int fps) {
		mc().options.maxFramerate = fps;
	}

	@Override
	public boolean windowFocused() {
		return Display.isActive();
	}

	@Override
	public boolean viewBobbing() {
		return mc().options.bobView;
	}

	@Override
	public void setViewBobbing(boolean on) {
		mc().options.bobView = on;
	}

	@Override
	public boolean entityShadows() {
		return mc().options.entityShadows;
	}

	@Override
	public void setEntityShadows(boolean on) {
		mc().options.entityShadows = on;
	}

	@Override
	public double sensitivity() {
		return mc().options.sensitivity;
	}

	@Override
	public void setSensitivity(double value) {
		mc().options.sensitivity = (float) Math.max(0.0, Math.min(1.0, value));
	}

	@Override
	public void playAlert(int kind, float pitch) {
		String id = kind == 0 ? "random.orb" : kind == 1 ? "note.pling" : "gui.button.press";
		mc().getSoundManager().play(PositionedSoundInstance.master(new Identifier(id), pitch));
	}

	// ---- chat, HUD, world and inventory mods

	@Override
	public String worldId() {
		if (mc().world == null) return null;
		if (mc().isIntegratedServerRunning() && mc().getServer() != null) return "world:" + mc().getServer().getLevelName();
		ServerInfo server = mc().getCurrentServerEntry();
		return server == null ? null : "server:" + server.address.toLowerCase(java.util.Locale.ROOT);
	}

	private static String legacy(Object o) {
		if (o == null) return "";
		if (o instanceof net.minecraft.text.Text) return ((net.minecraft.text.Text) o).asFormattedString();
		return o.toString();
	}

	@Override
	public int richWidth(Object component) {
		return mc().textRenderer.getStringWidth(legacy(component));
	}

	@Override
	public int richLineCount(Object component, int maxWidth) {
		return Math.max(1, mc().textRenderer.wrapLines(legacy(component), maxWidth).size());
	}

	@Override
	public dev.polarclient.platform.Sidebar sidebar() {
		if (mc().world == null || player() == null) return null;
		net.minecraft.scoreboard.Scoreboard board = mc().world.getScoreboard();
		net.minecraft.scoreboard.ScoreboardObjective objective = null;
		net.minecraft.scoreboard.Team team = board.getPlayerTeam(player().getTranslationKey());
		if (team != null && team.getFormatting().getColorIndex() >= 0) objective = board.getObjectiveForSlot(3 + team.getFormatting().getColorIndex());
		if (objective == null) objective = board.getObjectiveForSlot(1);
		if (objective == null) return null;
		List<net.minecraft.scoreboard.ScoreboardPlayerScore> scores = new ArrayList<net.minecraft.scoreboard.ScoreboardPlayerScore>();
		for (net.minecraft.scoreboard.ScoreboardPlayerScore sc : board.getAllPlayerScores(objective)) {
			if (sc.getPlayerName() != null && !sc.getPlayerName().startsWith("#")) scores.add(sc);
		}
		// the game sorts lowest first and shows the last 15, bottom to top
		if (scores.size() > 15) scores = new ArrayList<net.minecraft.scoreboard.ScoreboardPlayerScore>(scores.subList(scores.size() - 15, scores.size()));
		java.util.Collections.reverse(scores);
		dev.polarclient.platform.Sidebar out = new dev.polarclient.platform.Sidebar(objective.getDisplayName());
		for (net.minecraft.scoreboard.ScoreboardPlayerScore sc : scores) {
			out.add(net.minecraft.scoreboard.Team.decorateName(board.getPlayerTeam(sc.getPlayerName()), sc.getPlayerName()), "\u00a7c" + sc.getScore());
		}
		return out;
	}

	@Override
	public List<dev.polarclient.platform.BossBarView> bossBars() {
		List<dev.polarclient.platform.BossBarView> out = new ArrayList<dev.polarclient.platform.BossBarView>();
		if (net.minecraft.entity.boss.BossBar.name != null && net.minecraft.entity.boss.BossBar.framesToLive > 0) {
			out.add(new dev.polarclient.platform.BossBarView(net.minecraft.entity.boss.BossBar.name, net.minecraft.entity.boss.BossBar.percent, 5, 0));
		}
		return out;
	}

	private static final java.util.Comparator<PlayerListEntry> TAB_ORDER = new java.util.Comparator<PlayerListEntry>() {
		@Override
		public int compare(PlayerListEntry a, PlayerListEntry b) {
			int sa = a.getGameMode() == net.minecraft.world.level.LevelInfo.GameMode.SPECTATOR ? 1 : 0;
			int sb = b.getGameMode() == net.minecraft.world.level.LevelInfo.GameMode.SPECTATOR ? 1 : 0;
			if (sa != sb) return sa - sb;
			String ta = a.getScoreboardTeam() == null ? "" : a.getScoreboardTeam().getName();
			String tb = b.getScoreboardTeam() == null ? "" : b.getScoreboardTeam().getName();
			int t = ta.compareTo(tb);
			return t != 0 ? t : a.getProfile().getName().compareToIgnoreCase(b.getProfile().getName());
		}
	};

	@Override
	public dev.polarclient.platform.TabListView tabList() {
		if (player() == null || player().networkHandler == null) return null;
		net.minecraft.client.gui.hud.PlayerListHud hud = mc().inGameHud.getPlayerListWidget();
		dev.polarclient.impl.mixin.PlayerListHudAccessor acc = (dev.polarclient.impl.mixin.PlayerListHudAccessor) hud;
		dev.polarclient.platform.TabListView out = new dev.polarclient.platform.TabListView(
			acc.polar$header() == null ? null : acc.polar$header().asFormattedString(), acc.polar$footer() == null ? null : acc.polar$footer().asFormattedString());
		List<PlayerListEntry> players = new ArrayList<PlayerListEntry>(player().networkHandler.getPlayerList());
		java.util.Collections.sort(players, TAB_ORDER);
		for (PlayerListEntry e : players.subList(0, Math.min(80, players.size()))) {
			out.entries.add(new dev.polarclient.platform.TabListView.Entry(hud.getPlayerName(e), e.getProfile().getName(), e.getProfile().getId(), e.getLatency(),
				e.getGameMode() == net.minecraft.world.level.LevelInfo.GameMode.SPECTATOR));
		}
		return out;
	}

	@Override
	public boolean tabListKeyDown() {
		return mc().options.playerListKey.isPressed();
	}

	@Override
	public float[] project(double x, double y, double z) {
		net.minecraft.client.util.Window w = new net.minecraft.client.util.Window(mc());
		return Render3d.project(x, y, z, w.getWidth(), w.getHeight());
	}

	@Override
	public List<ItemView> containerContents(ItemView item) {
		return null; // no shulker boxes in 1.8.9
	}

	@Override
	public void setHurtColor(int argb) {
		Tints.hurt = argb;
	}

	@Override
	public void setGlintColor(int rgb) {
		Tints.glint = rgb;
	}

	@Override
	public boolean hitboxesShown() {
		return mc().getEntityRenderManager().getRenderHitboxes();
	}

	@Override
	public boolean setHitboxesShown(boolean on) {
		mc().getEntityRenderManager().setRenderHitboxes(on);
		return true;
	}

	@Override
	public boolean dimensionHasCeiling() {
		// the Nether is the only 1.8 dimension without sky light
		return mc().world != null && mc().world.dimension.hasNoSkylight();
	}

	@Override
	public boolean mapChunk(int chunkX, int chunkZ, int scanFromY, int[] rgb, int[] height, int[] water) {
		return MapReader.read(chunkX, chunkZ, scanFromY, rgb, height, water);
	}

	@Override
	public Object createTexture(String name, int width, int height) {
		return MapReader.createTexture(name, width, height);
	}

	@Override
	public void uploadTexture(Object texture, int[] argb) {
		MapReader.upload(texture, argb);
	}

	@Override
	public void destroyTexture(Object texture) {
		MapReader.destroy(texture);
	}

	// ---- Polar cosmetics and friends

	@Override
	public List<UUID> worldPlayerIds() {
		MinecraftClient mc = MinecraftClient.getInstance();
		List<UUID> out = new ArrayList<>();
		if (mc.world == null) return out;
		for (Object pl : mc.world.playerEntities) out.add(((net.minecraft.entity.player.PlayerEntity) pl).getUuid());
		return out;
	}

	@Override
	public void chatLine(String text) {
		MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(new net.minecraft.text.LiteralText(text));
	}

	@Override
	public void joinServer(String address) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world != null) {
			mc.world.disconnect();
			mc.connect((net.minecraft.client.world.ClientWorld) null);
		}
		mc.setScreen(new net.minecraft.client.gui.screen.ConnectScreen(new net.minecraft.client.gui.screen.TitleScreen(), mc, new net.minecraft.client.network.ServerInfo(address, address, false)));
	}

	@Override
	public String polarKeyName(PolarKey key) {
		int code = Keys.get(key).getCode();
		return code == 0 ? null : net.minecraft.client.option.GameOptions.getFormattedNameForKeyCode(code);
	}
}
