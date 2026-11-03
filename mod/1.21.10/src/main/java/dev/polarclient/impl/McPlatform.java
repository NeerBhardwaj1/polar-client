package dev.polarclient.impl;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.realmsclient.RealmsMainScreen;
import dev.polarclient.account.AccountSwitcher;
import dev.polarclient.account.LauncherBridge;
import dev.polarclient.gfx.UiFont;
import dev.polarclient.gui.View;
import dev.polarclient.impl.mixin.MinecraftAccessor;
import dev.polarclient.platform.EffectView;
import dev.polarclient.platform.GameKey;
import dev.polarclient.platform.ItemKind;
import dev.polarclient.platform.ItemView;
import dev.polarclient.platform.Platform;
import dev.polarclient.platform.PolarKey;
import dev.polarclient.platform.ServerBackend;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.SharedConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.User;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.packs.repository.Pack;

/** The game, as Polar's shared code sees it, on Minecraft 1.21.10. */
public final class McPlatform extends Platform {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private final Map<ItemKind, ItemView> kindIcons = new EnumMap<>(ItemKind.class);

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	// ------------------------------------------------------------------ game & window

	@Override
	public String minecraftVersion() {
		return SharedConstants.getCurrentVersion().name();
	}

	@Override
	public int fps() {
		return mc().getFps();
	}

	@Override
	public int guiScale() {
		return mc().getWindow().getGuiScale();
	}

	@Override
	public void execute(Runnable task) {
		mc().execute(task);
	}

	@Override
	public void playClickSound() {
		mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
	}

	@Override
	public void quit() {
		mc().stop();
	}

	@Override
	public boolean debugScreenOpen() {
		return mc().getDebugOverlay().showDebugScreen();
	}

	@Override
	public String clipboard() {
		return mc().keyboardHandler.getClipboard();
	}

	@Override
	public void setClipboard(String text) {
		mc().keyboardHandler.setClipboard(text);
	}

	// ------------------------------------------------------------------ text

	@Override
	public int textWidth(String s, UiFont font) {
		return font == UiFont.VANILLA ? mc().font.width(s) : mc().font.width(McGfx.styled(s, font));
	}

	@Override
	public float exactTextWidth(String s, UiFont font) {
		return mc().font.getSplitter().stringWidth(McGfx.styled(s, font));
	}

	@Override
	public int vanillaLineHeight() {
		return mc().font.lineHeight;
	}

	@Override
	public String trimToWidth(String s, int maxWidth) {
		return mc().font.plainSubstrByWidth(s, maxWidth);
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
		return mc().screen;
	}

	@Override
	public View currentView() {
		return mc().screen instanceof ViewScreen s ? s.view : null;
	}

	@Override
	public boolean screenOpen() {
		return mc().screen != null;
	}

	@Override
	public void openSingleplayer(Object parent) {
		mc().setScreen(new SelectWorldScreen((Screen) parent));
	}

	@Override
	public void openMultiplayer(Object parent) {
		// Polar's list replaces the vanilla one (GuiMixin) when it's turned on in Polar Settings
		Screen p = (Screen) parent;
		mc().setScreen(mc().options.skipMultiplayerWarning ? new JoinMultiplayerScreen(p) : new SafetyScreen(p));
	}

	@Override
	public void openRealms(Object parent) {
		mc().setScreen(new RealmsMainScreen((Screen) parent));
	}

	@Override
	public void openOptions(Object parent) {
		mc().setScreen(new OptionsScreen((Screen) parent, mc().options));
	}

	@Override
	public void openSkinOptions(Object parent) {
		mc().setScreen(new SkinCustomizationScreen((Screen) parent, mc().options));
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

	private static LocalPlayer player() {
		return mc().player;
	}

	@Override
	public boolean inWorld() {
		return mc().level != null;
	}

	@Override
	public boolean hasPlayer() {
		return player() != null;
	}

	@Override
	public boolean isSingleplayer() {
		return mc().getSingleplayerServer() != null;
	}

	@Override
	public String serverAddress() {
		ServerData server = mc().getCurrentServer();
		return server == null ? null : server.ip;
	}

	@Override
	public int ping() {
		ClientPacketListener connection = mc().getConnection();
		if (connection == null || player() == null) return -1;
		PlayerInfo info = connection.getPlayerInfo(player().getUUID());
		return info == null ? -1 : info.getLatency();
	}

	@Override
	public double playerX() {
		return player().getX();
	}

	@Override
	public double playerY() {
		return player().getY();
	}

	@Override
	public double playerZ() {
		return player().getZ();
	}

	@Override
	public float playerYaw() {
		return player().getYRot();
	}

	@Override
	public float playerPitch() {
		return player().getXRot();
	}

	@Override
	public String facing() {
		return player().getDirection().getName();
	}

	@Override
	public String biome() {
		if (mc().level == null || player() == null) return null;
		return mc().level.getBiome(player().blockPosition()).unwrapKey().map(k -> k.location().getPath()).orElse(null);
	}

	@Override
	public double horizontalSpeed() {
		Vec3 v = player().getDeltaMovement();
		return Math.sqrt(v.x * v.x + v.z * v.z) * 20.0;
	}

	@Override
	public float health() {
		return player().getHealth();
	}

	@Override
	public boolean creativeOrSpectator() {
		return player().isCreative() || player().isSpectator();
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
		return new ItemView(s, s.isDamageableItem(), s.getMaxDamage(), s.getDamageValue(), s.getCount());
	}

	@Override
	public List<ItemView> armorAndHand(boolean includeHand) {
		List<ItemView> out = new ArrayList<>();
		LocalPlayer p = player();
		if (p == null) return out;
		for (EquipmentSlot slot : ARMOR) {
			ItemStack s = p.getItemBySlot(slot);
			if (!s.isEmpty()) out.add(view(s));
		}
		if (includeHand && !p.getMainHandItem().isEmpty()) out.add(view(p.getMainHandItem()));
		return out;
	}

	@Override
	public List<ItemView> previewArmor(boolean includeHand) {
		List<ItemView> out = new ArrayList<>();
		out.add(view(new ItemStack(Items.DIAMOND_HELMET)));
		out.add(view(new ItemStack(Items.DIAMOND_CHESTPLATE)));
		out.add(view(new ItemStack(Items.DIAMOND_LEGGINGS)));
		out.add(view(new ItemStack(Items.DIAMOND_BOOTS)));
		if (includeHand) out.add(view(new ItemStack(Items.DIAMOND_SWORD)));
		return out;
	}

	private static Item item(ItemKind kind) {
		switch (kind) {
			case TOTEM: return Items.TOTEM_OF_UNDYING;
			case ENDER_PEARL: return Items.ENDER_PEARL;
			case GOLDEN_APPLE: return Items.GOLDEN_APPLE;
			case SHIELD: return Items.SHIELD;
			case CHORUS_FRUIT: return Items.CHORUS_FRUIT;
			default: return Items.ARROW;
		}
	}

	@Override
	public int countItems(ItemKind kind) {
		LocalPlayer p = player();
		if (p == null) return 0;
		Inventory inv = p.getInventory();
		Item target = item(kind);
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.getItem() == target) n += s.getCount();
		}
		return n;
	}

	@Override
	public ItemView itemOf(ItemKind kind) {
		return kindIcons.computeIfAbsent(kind, k -> view(new ItemStack(item(k))));
	}

	private static EffectView effect(MobEffectInstance e) {
		float tickrate = mc().level != null ? mc().level.tickRateManager().tickrate() : 20f;
		String name = EffectView.withLevel(e.getEffect().value().getDisplayName().getString(), e.getAmplifier());
		String duration = MobEffectUtil.formatDuration(e, 1.0f, tickrate).getString();
		return new EffectView(e, name, duration, !e.isInfiniteDuration() && e.endsWithin(200));
	}

	@Override
	public List<EffectView> effects(boolean hideAmbient) {
		List<EffectView> out = new ArrayList<>();
		LocalPlayer p = player();
		if (p == null) return out;
		for (MobEffectInstance e : p.getActiveEffects()) {
			if (hideAmbient && e.isAmbient()) continue;
			out.add(effect(e));
		}
		return out;
	}

	@Override
	public List<EffectView> previewEffects() {
		List<EffectView> out = new ArrayList<>();
		out.add(effect(new MobEffectInstance(MobEffects.SPEED, 1800, 1)));
		out.add(effect(new MobEffectInstance(MobEffects.STRENGTH, 900, 0)));
		out.add(effect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 3600, 0)));
		return out;
	}

	@Override
	public void sendChat(String message) {
		ClientPacketListener connection = mc().getConnection();
		if (connection != null) connection.sendChat(message);
	}

	// ------------------------------------------------------------------ keys & options

	private static KeyMapping mapping(GameKey key) {
		Options o = mc().options;
		switch (key) {
			case FORWARD: return o.keyUp;
			case LEFT: return o.keyLeft;
			case BACK: return o.keyDown;
			case RIGHT: return o.keyRight;
			case ATTACK: return o.keyAttack;
			case USE: return o.keyUse;
			case JUMP: return o.keyJump;
			case SNEAK: return o.keyShift;
			default: return o.keySprint;
		}
	}

	@Override
	public boolean keyDown(GameKey key) {
		return mapping(key).isDown();
	}

	@Override
	public String keyLabel(GameKey key) {
		return mapping(key).getTranslatedKeyMessage().getString();
	}

	@Override
	public void setKeyDown(GameKey key, boolean down) {
		mapping(key).setDown(down);
	}

	@Override
	public boolean physicalKeyDown(GameKey key) {
		InputConstants.Key bound = KeyBindingHelper.getBoundKeyOf(mapping(key));
		if (bound.getType() == InputConstants.Type.KEYSYM) return InputConstants.isKeyDown(mc().getWindow(), bound.getValue());
		return mapping(key).isDown();
	}

	@Override
	public boolean polarKeyDown(PolarKey key) {
		return Keys.get(key).isDown();
	}

	@Override
	public boolean consumePolarKey(PolarKey key) {
		return Keys.get(key).consumeClick();
	}

	@Override
	public int perspective() {
		CameraType type = mc().options.getCameraType();
		return type == CameraType.FIRST_PERSON ? 0 : type == CameraType.THIRD_PERSON_BACK ? 1 : 2;
	}

	@Override
	public void setPerspective(int perspective) {
		mc().options.setCameraType(perspective == 0 ? CameraType.FIRST_PERSON : perspective == 1 ? CameraType.THIRD_PERSON_BACK : CameraType.THIRD_PERSON_FRONT);
	}

	@Override
	public boolean smoothCamera() {
		return mc().options.smoothCamera;
	}

	@Override
	public void setSmoothCamera(boolean on) {
		mc().options.smoothCamera = on;
	}

	@Override
	public boolean hasToggleOptions() {
		return true;
	}

	@Override
	public boolean toggleSprint() {
		return mc().options.toggleSprint().get();
	}

	@Override
	public void setToggleSprint(boolean on) {
		mc().options.toggleSprint().set(on);
	}

	@Override
	public boolean toggleSneak() {
		return mc().options.toggleCrouch().get();
	}

	@Override
	public void setToggleSneak(boolean on) {
		mc().options.toggleCrouch().set(on);
	}

	@Override
	public boolean hasDamageTiltOption() {
		return true;
	}

	@Override
	public double damageTilt() {
		return mc().options.damageTiltStrength().get();
	}

	@Override
	public void setDamageTilt(double value) {
		mc().options.damageTiltStrength().set(value);
	}

	// ------------------------------------------------------------------ account

	@Override
	public String userName() {
		return mc().getUser().getName();
	}

	@Override
	public UUID userId() {
		return mc().getUser().getProfileId();
	}

	/** Swaps the signed-in session (user, services, chat-signing keys) the way Minecraft sets them up at startup. */
	@Override
	public void switchSession(LauncherBridge.Session s) {
		Minecraft mc = mc();
		UUID id = AccountSwitcher.parse(s.uuid());
		boolean offline = s.offline();
		User old = mc.getUser();
		Optional<String> xuid = s.xuid().isEmpty() || "0".equals(s.xuid()) ? Optional.empty() : Optional.of(s.xuid());
		User user = new User(s.name(), id, s.accessToken(), xuid, old.getClientId());

		UserApiService api = offline ? UserApiService.OFFLINE
			: new YggdrasilAuthenticationService(mc.getProxy()).createUserApiService(s.accessToken());
		MinecraftAccessor access = (MinecraftAccessor) mc;
		access.polar$setUser(user);
		access.polar$setUserApiService(api);
		access.polar$setUserPropertiesFuture(CompletableFuture.supplyAsync(() -> {
			try {
				return api.fetchProperties();
			} catch (AuthenticationException e) {
				return UserApiService.OFFLINE_PROPERTIES;
			}
		}, Util.nonCriticalIoPool()));
		CompletableFuture<ProfileResult> profile = offline ? CompletableFuture.completedFuture(null)
			: CompletableFuture.supplyAsync(() -> mc.services().sessionService().fetchProfile(id, true), Util.nonCriticalIoPool());
		access.polar$setProfileFuture(profile);
		access.polar$setProfileKeyPairManager(offline ? ProfileKeyPairManager.EMPTY_KEY_MANAGER
			: ProfileKeyPairManager.create(api, user, mc.gameDirectory.toPath()));
		access.polar$setReportingContext(ReportingContext.create(ReportEnvironment.local(), api));
	}

	// ------------------------------------------------------------------ more player & world info (HUD mods)

	@Override
	public float maxHealth() {
		return player() == null ? 20f : player().getMaxHealth();
	}

	@Override
	public float absorption() {
		return player() == null ? 0f : player().getAbsorptionAmount();
	}

	@Override
	public int foodLevel() {
		return player() == null ? 20 : player().getFoodData().getFoodLevel();
	}

	@Override
	public float saturation() {
		return player() == null ? 0f : player().getFoodData().getSaturationLevel();
	}

	@Override
	public int entityCount() {
		return mc().level == null ? 0 : mc().level.getEntityCount();
	}

	@Override
	public ItemView heldItem() {
		if (player() == null) return null;
		ItemStack s = player().getMainHandItem();
		return s.isEmpty() ? null : view(s);
	}

	@Override
	public String itemName(ItemView item) {
		return item != null && item.handle instanceof ItemStack ? ((ItemStack) item.handle).getHoverName().getString() : "";
	}

	@Override
	public List<String> resourcePacks() {
		List<String> out = new ArrayList<>();
		for (Pack pack : mc().getResourcePackRepository().getSelectedPacks()) {
			if (pack.getId().startsWith("file/")) out.add(0, pack.getTitle().getString()); // last selected = top
		}
		return out;
	}

	@Override
	public boolean hasAttackCooldown() {
		return true;
	}

	@Override
	public float attackStrength() {
		return player() == null ? 1f : player().getAttackStrengthScale(0f);
	}

	@Override
	public float itemCooldown(ItemKind kind) {
		if (player() == null) return 0f;
		return player().getCooldowns().getCooldownPercent(new ItemStack(item(kind)), 0f);
	}

	@Override
	public double worldBorderDistance() {
		if (mc().level == null || player() == null) return -1;
		return mc().level.getWorldBorder().getDistanceToBorder(player());
	}

	@Override
	public String dimension() {
		return mc().level == null ? "" : mc().level.dimension().location().getPath();
	}

	@Override
	public int maxFps() {
		return mc().options.framerateLimit().get();
	}

	@Override
	public void setMaxFps(int fps) {
		// straight to the limiter, so the player's own setting in options.txt is never changed
		mc().getFramerateLimitTracker().setFramerateLimit(fps);
	}

	@Override
	public boolean windowFocused() {
		return mc().isWindowActive();
	}

	@Override
	public boolean viewBobbing() {
		return mc().options.bobView().get();
	}

	@Override
	public void setViewBobbing(boolean on) {
		mc().options.bobView().set(on);
	}

	@Override
	public boolean entityShadows() {
		return mc().options.entityShadows().get();
	}

	@Override
	public void setEntityShadows(boolean on) {
		mc().options.entityShadows().set(on);
	}

	@Override
	public double sensitivity() {
		return mc().options.sensitivity().get();
	}

	@Override
	public void setSensitivity(double value) {
		mc().options.sensitivity().set(Math.max(0.0, Math.min(1.0, value)));
	}

	@Override
	public void playAlert(int kind, float pitch) {
		if (kind == 0) mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, pitch));
		else if (kind == 1) mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, pitch));
		else mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
	}

	// ---- chat, HUD, world and inventory mods

	@Override
	public String worldId() {
		if (mc().level == null) return null;
		if (mc().getSingleplayerServer() != null) return "world:" + mc().getSingleplayerServer().getWorldData().getLevelName();
		ServerData server = mc().getCurrentServer();
		return server == null ? null : "server:" + server.ip.toLowerCase(java.util.Locale.ROOT);
	}

	private static net.minecraft.network.chat.Component component(Object o) {
		if (o instanceof net.minecraft.network.chat.Component c) return c;
		return net.minecraft.network.chat.Component.literal(o == null ? "" : o.toString());
	}

	@Override
	public int richWidth(Object component) {
		if (component instanceof String str) return mc().font.width(str);
		return mc().font.width(component(component));
	}

	@Override
	public int richLineCount(Object component, int maxWidth) {
		return Math.max(1, mc().font.split(component(component), maxWidth).size());
	}

	private static final java.util.Comparator<net.minecraft.world.scores.PlayerScoreEntry> SCORE_ORDER =
		java.util.Comparator.comparing(net.minecraft.world.scores.PlayerScoreEntry::value).reversed()
			.thenComparing(net.minecraft.world.scores.PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

	@Override
	public dev.polarclient.platform.Sidebar sidebar() {
		if (mc().level == null || player() == null) return null;
		net.minecraft.world.scores.Scoreboard board = mc().level.getScoreboard();
		net.minecraft.world.scores.Objective objective = null;
		net.minecraft.world.scores.PlayerTeam team = board.getPlayersTeam(player().getScoreboardName());
		if (team != null) {
			net.minecraft.world.scores.DisplaySlot slot = net.minecraft.world.scores.DisplaySlot.teamColorToSlot(team.getColor());
			if (slot != null) objective = board.getDisplayObjective(slot);
		}
		if (objective == null) objective = board.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
		if (objective == null) return null;
		net.minecraft.network.chat.numbers.NumberFormat format = objective.numberFormatOrDefault(net.minecraft.network.chat.numbers.StyledFormat.SIDEBAR_DEFAULT);
		dev.polarclient.platform.Sidebar out = new dev.polarclient.platform.Sidebar(objective.getDisplayName());
		board.listPlayerScores(objective).stream().filter(e -> !e.isHidden()).sorted(SCORE_ORDER).limit(15).forEach(e ->
			out.add(net.minecraft.world.scores.PlayerTeam.formatNameForTeam(board.getPlayersTeam(e.owner()), e.ownerName()), e.formatValue(format)));
		return out;
	}

	@Override
	public List<dev.polarclient.platform.BossBarView> bossBars() {
		List<dev.polarclient.platform.BossBarView> out = new ArrayList<>();
		if (mc().gui == null) return out;
		for (net.minecraft.client.gui.components.LerpingBossEvent e : ((dev.polarclient.impl.mixin.BossHealthOverlayAccessor) mc().gui.getBossOverlay()).polar$events().values()) {
			out.add(new dev.polarclient.platform.BossBarView(e.getName(), e.getProgress(), e.getColor().ordinal(), notches(e.getOverlay())));
		}
		return out;
	}

	private static int notches(net.minecraft.world.BossEvent.BossBarOverlay overlay) {
		switch (overlay) {
			case NOTCHED_6: return 6;
			case NOTCHED_10: return 10;
			case NOTCHED_12: return 12;
			case NOTCHED_20: return 20;
			default: return 0;
		}
	}

	private static final java.util.Comparator<PlayerInfo> TAB_ORDER = java.util.Comparator.comparingInt((PlayerInfo p) -> -p.getTabListOrder())
		.thenComparingInt(p -> p.getGameMode() == net.minecraft.world.level.GameType.SPECTATOR ? 1 : 0)
		.thenComparing(p -> p.getTeam() == null ? "" : p.getTeam().getName())
		.thenComparing(p -> p.getProfile().name(), String::compareToIgnoreCase);

	@Override
	public dev.polarclient.platform.TabListView tabList() {
		ClientPacketListener connection = mc().getConnection();
		if (connection == null || player() == null) return null;
		net.minecraft.client.gui.components.PlayerTabOverlay overlay = mc().gui.getTabList();
		dev.polarclient.impl.mixin.PlayerTabOverlayAccessor acc = (dev.polarclient.impl.mixin.PlayerTabOverlayAccessor) overlay;
		dev.polarclient.platform.TabListView out = new dev.polarclient.platform.TabListView(acc.polar$header(), acc.polar$footer());
		connection.getListedOnlinePlayers().stream().sorted(TAB_ORDER).limit(80).forEach(info -> out.entries.add(new dev.polarclient.platform.TabListView.Entry(
			overlay.getNameForDisplay(info), info.getProfile().name(), info.getProfile().id(), info.getLatency(), info.getGameMode() == net.minecraft.world.level.GameType.SPECTATOR)));
		return out;
	}

	@Override
	public boolean tabListKeyDown() {
		return mc().options.keyPlayerList.isDown();
	}

	@Override
	public float[] project(double x, double y, double z) {
		return Render3d.project(x, y, z, mc().getWindow().getGuiScaledWidth(), mc().getWindow().getGuiScaledHeight());
	}

	@Override
	public List<ItemView> containerContents(ItemView item) {
		if (item == null || !(item.handle instanceof ItemStack stack)) return null;
		net.minecraft.world.item.component.ItemContainerContents contents = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
		if (contents == null) return null;
		net.minecraft.core.NonNullList<ItemStack> slots = net.minecraft.core.NonNullList.withSize(27, ItemStack.EMPTY);
		contents.copyInto(slots);
		List<ItemView> out = new ArrayList<>();
		for (ItemStack s : slots) out.add(s.isEmpty() ? null : view(s));
		return out;
	}

	@Override
	public void setHurtColor(int argb) {
		Tints.hurt(argb);
	}

	@Override
	public void setGlintColor(int rgb) {
		Tints.glint(rgb);
	}

	@Override
	public boolean hitboxesShown() {
		return mc().debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES);
	}

	@Override
	public boolean setHitboxesShown(boolean on) {
		// the same toggle as F3+B, which the game blocks under reduced debug info
		if (on && mc().player != null && mc().player.isReducedDebugInfo()) return false;
		if (hitboxesShown() != on) mc().debugEntries.toggleStatus(DebugScreenEntries.ENTITY_HITBOXES);
		return hitboxesShown() == on;
	}

	@Override
	public boolean dimensionHasCeiling() {
		return mc().level != null && mc().level.dimensionType().hasCeiling();
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
		Minecraft mc = Minecraft.getInstance();
		List<UUID> out = new ArrayList<>();
		if (mc.level == null) return out;
		for (net.minecraft.world.entity.player.Player pl : mc.level.players()) out.add(pl.getUUID());
		return out;
	}

	@Override
	public void chatLine(String text) {
		Minecraft.getInstance().gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(text));
	}

	@Override
	public void joinServer(String address) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) mc.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
		net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new net.minecraft.client.gui.screens.TitleScreen(), mc,
			net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address), new ServerData(address, address, ServerData.Type.OTHER), false, null);
	}

	@Override
	public String polarKeyName(PolarKey key) {
		KeyMapping k = Keys.get(key);
		return k.isUnbound() ? null : k.getTranslatedKeyMessage().getString();
	}
}
