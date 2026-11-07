package dev.polarclient.impl;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.OfflineSocialInteractions;
import com.mojang.authlib.minecraft.SocialInteractionsService;
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
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.SkinCustomizationScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.packs.repository.Pack;

/** The game, as Polar's shared code sees it, on Minecraft 1.16.5. */
public final class McPlatform extends Platform {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private final Map<ItemKind, ItemView> kindIcons = new EnumMap<ItemKind, ItemView>(ItemKind.class);

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	// ------------------------------------------------------------------ game & window

	@Override
	public String minecraftVersion() {
		return SharedConstants.getCurrentVersion().getName();
	}

	@Override
	public int fps() {
		return MinecraftAccessor.polar$fps();
	}

	@Override
	public int guiScale() {
		return (int) mc().getWindow().getGuiScale();
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
		return mc().options.renderDebug;
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
		return mc().screen instanceof ViewScreen ? ((ViewScreen) mc().screen).view : null;
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
		// Polar's list replaces the vanilla one (ScreenSwapMixin) when it's turned on in Polar Settings
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
		return false; // no streamer option before 1.18
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
		return player().yRot;
	}

	@Override
	public float playerPitch() {
		return player().xRot;
	}

	@Override
	public String facing() {
		return player().getDirection().getName();
	}

	@Override
	public String biome() {
		if (mc().level == null || player() == null) return null;
		Biome biome = mc().level.getBiome(player().blockPosition());
		ResourceLocation id = mc().level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY).getKey(biome);
		return id == null ? null : id.getPath();
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
		List<ItemView> out = new ArrayList<ItemView>();
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
		List<ItemView> out = new ArrayList<ItemView>();
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
		Inventory inv = p.inventory;
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
		ItemView v = kindIcons.get(kind);
		if (v == null) kindIcons.put(kind, v = view(new ItemStack(item(kind))));
		return v;
	}

	private static EffectView effect(MobEffectInstance e) {
		String name = EffectView.withLevel(e.getEffect().getDisplayName().getString(), e.getAmplifier());
		return new EffectView(e, name, MobEffectUtil.formatDuration(e, 1.0f), e.getDuration() <= 200);
	}

	@Override
	public List<EffectView> effects(boolean hideAmbient) {
		List<EffectView> out = new ArrayList<EffectView>();
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
		List<EffectView> out = new ArrayList<EffectView>();
		out.add(effect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1800, 1)));
		out.add(effect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 900, 0)));
		out.add(effect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 3600, 0)));
		return out;
	}

	@Override
	public void sendChat(String message) {
		if (player() != null) player().chat(message);
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
		if (bound.getType() == InputConstants.Type.KEYSYM) return InputConstants.isKeyDown(mc().getWindow().getWindow(), bound.getValue());
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
		return mc().options.toggleSprint;
	}

	@Override
	public void setToggleSprint(boolean on) {
		mc().options.toggleSprint = on;
	}

	@Override
	public boolean toggleSneak() {
		return mc().options.toggleCrouch;
	}

	@Override
	public void setToggleSneak(boolean on) {
		mc().options.toggleCrouch = on;
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
		return mc().getUser().getName();
	}

	@Override
	public UUID userId() {
		return AccountSwitcher.parse(mc().getUser().getUuid());
	}

	/** Swaps the signed-in session (user and social-interactions service) the way 1.16.5 sets them up at startup. */
	@Override
	public void switchSession(LauncherBridge.Session s) {
		Minecraft mc = mc();
		UUID id = AccountSwitcher.parse(s.uuid());
		boolean offline = s.offline();
		User user = new User(s.name(), id.toString().replace("-", ""), s.accessToken(), offline ? "legacy" : "mojang");
		SocialInteractionsService social;
		if (offline) {
			social = new OfflineSocialInteractions();
		} else {
			try {
				social = new YggdrasilAuthenticationService(mc.getProxy()).createSocialInteractionsService(s.accessToken());
			} catch (AuthenticationException e) {
				social = new OfflineSocialInteractions();
			}
		}
		MinecraftAccessor access = (MinecraftAccessor) mc;
		access.polar$setUser(user);
		access.polar$setSocialInteractionsService(social);
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
	public void forEachXpOrb(XpOrbVisitor visitor) {
		if (mc().level == null) return;
		for (net.minecraft.world.entity.Entity e : mc().level.entitiesForRendering()) {
			if (!(e instanceof net.minecraft.world.entity.ExperienceOrb)) continue;
			net.minecraft.world.entity.ExperienceOrb orb = (net.minecraft.world.entity.ExperienceOrb) e;
			visitor.orb(orb.getId(), orb.getX(), orb.getY(), orb.getZ(), orb.getValue());
		}
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
		List<String> out = new ArrayList<String>();
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
		return player().getCooldowns().getCooldownPercent(item(kind), 0f);
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
		return mc().options.framerateLimit;
	}

	@Override
	public void setMaxFps(int fps) {
		// the window's limit only, so the player's own setting in options.txt is never changed
		mc().getWindow().setFramerateLimit(fps);
	}

	@Override
	public boolean windowFocused() {
		return mc().isWindowActive();
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
		mc().options.sensitivity = Math.max(0.0, Math.min(1.0, value));
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
		if (o instanceof net.minecraft.network.chat.Component) return (net.minecraft.network.chat.Component) o;
		return new net.minecraft.network.chat.TextComponent(o == null ? "" : o.toString());
	}

	@Override
	public int richWidth(Object component) {
		if (component instanceof String) return mc().font.width((String) component);
		return mc().font.width(component(component));
	}

	@Override
	public int richLineCount(Object component, int maxWidth) {
		return Math.max(1, mc().font.split(component(component), maxWidth).size());
	}

	@Override
	public dev.polarclient.platform.Sidebar sidebar() {
		if (mc().level == null || player() == null) return null;
		net.minecraft.world.scores.Scoreboard board = mc().level.getScoreboard();
		net.minecraft.world.scores.Objective objective = null;
		net.minecraft.world.scores.PlayerTeam team = board.getPlayersTeam(player().getScoreboardName());
		if (team != null && team.getColor().getId() >= 0) objective = board.getDisplayObjective(3 + team.getColor().getId());
		if (objective == null) objective = board.getDisplayObjective(1);
		if (objective == null) return null;
		List<net.minecraft.world.scores.Score> scores = new ArrayList<>();
		for (net.minecraft.world.scores.Score sc : board.getPlayerScores(objective)) {
			if (sc.getOwner() != null && !sc.getOwner().startsWith("#")) scores.add(sc);
		}
		// the game sorts lowest first and shows the last 15, bottom to top
		if (scores.size() > 15) scores = new ArrayList<>(scores.subList(scores.size() - 15, scores.size()));
		java.util.Collections.reverse(scores);
		dev.polarclient.platform.Sidebar out = new dev.polarclient.platform.Sidebar(objective.getDisplayName());
		for (net.minecraft.world.scores.Score sc : scores) {
			out.add(net.minecraft.world.scores.PlayerTeam.formatNameForTeam(board.getPlayersTeam(sc.getOwner()), new net.minecraft.network.chat.TextComponent(sc.getOwner())),
				"\u00a7c" + sc.getScore());
		}
		return out;
	}

	@Override
	public List<dev.polarclient.platform.BossBarView> bossBars() {
		List<dev.polarclient.platform.BossBarView> out = new ArrayList<>();
		if (mc().gui == null) return out;
		for (net.minecraft.client.gui.components.LerpingBossEvent e : ((dev.polarclient.impl.mixin.BossHealthOverlayAccessor) mc().gui.getBossOverlay()).polar$events().values()) {
			out.add(new dev.polarclient.platform.BossBarView(e.getName(), e.getPercent(), e.getColor().ordinal(), notches(e.getOverlay())));
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

	private static final java.util.Comparator<PlayerInfo> TAB_ORDER = java.util.Comparator
		.comparingInt((PlayerInfo p) -> p.getGameMode() == net.minecraft.world.level.GameType.SPECTATOR ? 1 : 0)
		.thenComparing(p -> p.getTeam() == null ? "" : p.getTeam().getName())
		.thenComparing(p -> p.getProfile().getName(), String::compareToIgnoreCase);

	@Override
	public dev.polarclient.platform.TabListView tabList() {
		ClientPacketListener connection = mc().getConnection();
		if (connection == null || player() == null) return null;
		net.minecraft.client.gui.components.PlayerTabOverlay overlay = mc().gui.getTabList();
		dev.polarclient.impl.mixin.PlayerTabOverlayAccessor acc = (dev.polarclient.impl.mixin.PlayerTabOverlayAccessor) overlay;
		dev.polarclient.platform.TabListView out = new dev.polarclient.platform.TabListView(acc.polar$header(), acc.polar$footer());
		List<PlayerInfo> players = new ArrayList<>(connection.getOnlinePlayers());
		players.sort(TAB_ORDER);
		for (PlayerInfo info : players.subList(0, Math.min(80, players.size()))) {
			out.entries.add(new dev.polarclient.platform.TabListView.Entry(overlay.getNameForDisplay(info), info.getProfile().getName(), info.getProfile().getId(),
				info.getLatency(), info.getGameMode() == net.minecraft.world.level.GameType.SPECTATOR));
		}
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
		if (item == null || !(item.handle instanceof ItemStack)) return null;
		net.minecraft.nbt.CompoundTag tag = ((ItemStack) item.handle).getTagElement("BlockEntityTag");
		if (tag == null || !tag.contains("Items", 9)) return null;
		net.minecraft.core.NonNullList<ItemStack> slots = net.minecraft.core.NonNullList.withSize(27, ItemStack.EMPTY);
		net.minecraft.world.ContainerHelper.loadAllItems(tag, slots);
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
		return mc().getEntityRenderDispatcher().shouldRenderHitBoxes();
	}

	@Override
	public boolean setHitboxesShown(boolean on) {
		// the game skips drawing them under reduced debug info anyway
		if (on && mc().showOnlyReducedInfo()) return false;
		mc().getEntityRenderDispatcher().setRenderHitBoxes(on);
		return true;
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
		Minecraft.getInstance().gui.getChat().addMessage(new net.minecraft.network.chat.TextComponent(text));
	}

	@Override
	public void joinServer(String address) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) {
			mc.level.disconnect();
			mc.clearLevel();
		}
		mc.setScreen(new net.minecraft.client.gui.screens.ConnectScreen(new net.minecraft.client.gui.screens.TitleScreen(), mc, new ServerData(address, address, false)));
	}

	@Override
	public String polarKeyName(PolarKey key) {
		KeyMapping k = Keys.get(key);
		return k.isUnbound() ? null : k.getTranslatedKeyMessage().getString();
	}
}
