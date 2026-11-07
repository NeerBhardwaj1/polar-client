package dev.polarclient.platform;

import dev.polarclient.account.LauncherBridge;
import dev.polarclient.gfx.UiFont;
import dev.polarclient.gui.View;

import java.util.List;
import java.util.UUID;

/**
 * The game, as Polar's shared code sees it. Each Minecraft version (26.3, 1.21.11, 1.21.10, 1.16.5, 1.8.9) implements
 * this once on top of its own classes, so menus, HUD mods and settings are written a single time for all of them.
 * Everything is called on the game's render/client thread unless noted.
 */
public abstract class Platform {
	private static Platform instance;

	public static Platform get() {
		return instance;
	}

	public static void set(Platform platform) {
		instance = platform;
	}

	// ------------------------------------------------------------------ game & window

	/** Minecraft version name shown in menus, e.g. "26.3" or "1.8.9". */
	public abstract String minecraftVersion();

	public abstract int fps();

	public abstract int guiScale();

	/** Runs on the game thread (used to hand back results from background threads). */
	public abstract void execute(Runnable task);

	public abstract void playClickSound();

	public abstract void quit();

	/** True while the debug screen (F3) is open. */
	public abstract boolean debugScreenOpen();

	public abstract String clipboard();

	public abstract void setClipboard(String text);

	// ------------------------------------------------------------------ text measuring (Minecraft's font and Polar's)

	public abstract int textWidth(String s, UiFont font);

	/** Unrounded width, for letter-spaced text. */
	public abstract float exactTextWidth(String s, UiFont font);

	/** Line height of Minecraft's font (9). */
	public abstract int vanillaLineHeight();

	/** Longest prefix of s that fits maxWidth in Minecraft's font. */
	public abstract String trimToWidth(String s, int maxWidth);

	// ------------------------------------------------------------------ screens

	/** Opens a Polar view (wrapped in this version's screen class). */
	public abstract void open(View view);

	/** Shows a game screen previously handed out by {@link #currentScreen()} (null closes all screens). */
	public abstract void show(Object screen);

	/** The screen open right now (an opaque handle; null in game). */
	public abstract Object currentScreen();

	/** The Polar view open right now, or null if none is. */
	public abstract View currentView();

	public abstract boolean screenOpen();

	/** The vanilla screens Polar's menus link to; each returns to {@code parent}. */
	public abstract void openSingleplayer(Object parent);

	/** Opens Polar's server list, through Minecraft's multiplayer warning if it hasn't been accepted yet. */
	public abstract void openMultiplayer(Object parent);

	public abstract void openRealms(Object parent);

	public abstract void openOptions(Object parent);

	public abstract void openSkinOptions(Object parent);

	/** Lets the version's server list backend be created for the Polar server list. */
	public abstract ServerBackend createServerBackend(Object screenToReturnTo);

	public abstract boolean hideServerAddresses();

	// ------------------------------------------------------------------ world & player

	public abstract boolean inWorld();

	public abstract boolean hasPlayer();

	/** Playing on this computer (singleplayer / LAN host). */
	public abstract boolean isSingleplayer();

	/** Address of the server you're on, or null. */
	public abstract String serverAddress();

	/** Your latency to the server in ms, or -1. */
	public abstract int ping();

	public abstract double playerX();

	public abstract double playerY();

	public abstract double playerZ();

	public abstract float playerYaw();

	public abstract float playerPitch();

	/** "north", "south", "east" or "west". */
	public abstract String facing();

	/** Biome id path ("snowy_plains"), or null. */
	public abstract String biome();

	/** Horizontal speed in blocks per second. */
	public abstract double horizontalSpeed();

	public abstract float health();

	public abstract boolean creativeOrSpectator();

	public abstract boolean spectator();

	public abstract int hurtTime();

	public abstract boolean sprinting();

	/** Armour (head to feet), plus the held item if asked; empty slots skipped. */
	public abstract List<ItemView> armorAndHand(boolean includeHand);

	/** Sample items for the HUD editor preview: diamond helmet, chestplate, leggings, boots, sword. */
	public abstract List<ItemView> previewArmor(boolean includeHand);

	/** How many of an item kind ({@link ItemKind}) are in your inventory. */
	public abstract int countItems(ItemKind kind);

	public abstract ItemView itemOf(ItemKind kind);

	public abstract List<EffectView> effects(boolean hideAmbient);

	/** Speed II, Strength, Fire Resistance, for the HUD editor preview. */
	public abstract List<EffectView> previewEffects();

	public abstract void sendChat(String message);

	// ------------------------------------------------------------------ keys & options

	/** Is the game key (movement, attack, use, jump, sneak, sprint) held? */
	public abstract boolean keyDown(GameKey key);

	/** What the key is bound to, e.g. "W" or "LMB". */
	public abstract String keyLabel(GameKey key);

	public abstract void setKeyDown(GameKey key, boolean down);

	/** Is the key really held on the keyboard/mouse (ignoring {@link #setKeyDown})? Used where toggles are emulated. */
	public abstract boolean physicalKeyDown(GameKey key);

	public abstract boolean polarKeyDown(PolarKey key);

	/** Takes one press of a Polar key (true while there are unhandled presses). */
	public abstract boolean consumePolarKey(PolarKey key);

	/** 0 = first person, 1 = third person back, 2 = third person front. */
	public abstract int perspective();

	public abstract void setPerspective(int perspective);

	public abstract boolean smoothCamera();

	public abstract void setSmoothCamera(boolean on);

	/** Whether this version has the Toggle Sprint / Toggle Sneak options (1.15+). */
	public abstract boolean hasToggleOptions();

	public abstract boolean toggleSprint();

	public abstract void setToggleSprint(boolean on);

	public abstract boolean toggleSneak();

	public abstract void setToggleSneak(boolean on);

	/** Whether this version has the Damage Tilt accessibility option (1.19.4+); older ones scale the shake in a mixin. */
	public abstract boolean hasDamageTiltOption();

	public abstract double damageTilt();

	public abstract void setDamageTilt(double value);

	// ------------------------------------------------------------------ more player & world info (HUD mods)

	public abstract float maxHealth();

	/** Extra health from absorption hearts. */
	public abstract float absorption();

	/** Hunger bar, 0-20. */
	public abstract int foodLevel();

	public abstract float saturation();

	/** Entities loaded in the world around you (what F3 counts). */
	public abstract int entityCount();

	/** Something told about each XP orb in the world: its entity id, where it is and how much XP it holds. */
	public interface XpOrbVisitor {
		void orb(int id, double x, double y, double z, int value);
	}

	/** Calls {@code visitor} for each XP orb in the world (for XP Bottle Optimizer's orb merging). */
	public void forEachXpOrb(XpOrbVisitor visitor) {
	}

	/** The item in your main hand, or null if it's empty. */
	public abstract ItemView heldItem();

	/** The item's display name (custom name if it has one). */
	public abstract String itemName(ItemView item);

	/** Names of the resource packs in use, top one first, without the built-in ones. */
	public abstract List<String> resourcePacks();

	/** Whether this version has the 1.9 attack cooldown. */
	public abstract boolean hasAttackCooldown();

	/** Attack strength 0..1 (1 = fully charged). */
	public abstract float attackStrength();

	/** Cooldown left on an item, 0..1 (ender pearl, shield, chorus fruit); 0 where the version has none. */
	public abstract float itemCooldown(ItemKind kind);

	/** Blocks between you and the world border, or -1 if unknown. */
	public abstract double worldBorderDistance();

	/** Dimension id without namespace: "overworld", "the_nether", "the_end". */
	public abstract String dimension();

	// ------------------------------------------------------------------ more options

	/** Max framerate option (260 or more = unlimited). */
	public abstract int maxFps();

	public abstract void setMaxFps(int fps);

	public abstract boolean windowFocused();

	public abstract boolean viewBobbing();

	public abstract void setViewBobbing(boolean on);

	public abstract boolean entityShadows();

	public abstract void setEntityShadows(boolean on);

	/** Mouse sensitivity as the options slider shows it, 0..1 (0.5 = 100%). */
	public abstract double sensitivity();

	public abstract void setSensitivity(double value);

	/** UI sound for alerts: 0 = experience orb, 1 = note block pling, 2 = button click. */
	public abstract void playAlert(int kind, float pitch);

	// ------------------------------------------------------------------ account

	// ---- added for chat, HUD, world and inventory mods

	/** Where waypoints are kept: "server:<address>" in multiplayer, "world:<name>" in singleplayer, null outside a world. */
	public abstract String worldId();

	/** Width in GUI units of a formatted game text (see {@link dev.polarclient.gfx.Gfx#richLine}). */
	public abstract int richWidth(Object component);

	/** How many lines a formatted game text takes when wrapped to {@code maxWidth} (it may contain line breaks). */
	public abstract int richLineCount(Object component, int maxWidth);

	/** The sidebar scoreboard as the game would show it now, or null when there is none. */
	public abstract Sidebar sidebar();

	/** Boss bars currently shown, top to bottom. */
	public abstract List<BossBarView> bossBars();

	/** The player list, sorted like the game sorts it (at most 80 players). Null outside multiplayer worlds. */
	public abstract TabListView tabList();

	/** The "List Players" key (Tab) is held. */
	public abstract boolean tabListKeyDown();

	/**
	 * Where a point in the world appears on screen this frame, in GUI units: {x, y, depth}. Null when it is behind the
	 * camera or the world hasn't been drawn yet.
	 */
	public abstract float[] project(double x, double y, double z);

	/** The items inside a shulker box (or similar container item), in slot order; null if the item isn't one. */
	public abstract List<ItemView> containerContents(ItemView item);

	/** Colour of the red flash on hurt entities (ARGB); 0 restores the game's own. */
	public abstract void setHurtColor(int argb);

	/** Tint of the enchantment glint (RGB); -1 restores the game's own. */
	public abstract void setGlintColor(int rgb);

	// ---- hitboxes, minimap

	/** Whether entity hitboxes are showing (what F3+B toggles). */
	public abstract boolean hitboxesShown();

	/** Shows or hides hitboxes like F3+B. False when the server doesn't allow it (reduced debug info). */
	public abstract boolean setHitboxesShown(boolean on);

	/** Whether this dimension has a roof (the Nether): the minimap then shows the floor at your height. */
	public abstract boolean dimensionHasCeiling();

	/**
	 * Reads a loaded chunk for the minimap; false when it isn't loaded. For each column (index (z & 15) * 16 + (x & 15)):
	 * rgb = the map colour (0xRRGGBB) of the top block, 0 for none; height = that block's Y; water = how deep the water
	 * is there (0 = not water). scanFromY is Integer.MIN_VALUE for the surface, or the Y to look down from (Nether).
	 */
	public abstract boolean mapChunk(int chunkX, int chunkZ, int scanFromY, int[] rgb, int[] height, int[] water);

	/** A texture drawn from pixels made at runtime (the minimap), for {@link dev.polarclient.gfx.Gfx#blitHandle}. */
	public abstract Object createTexture(String name, int width, int height);

	/** Replaces the texture's pixels (ARGB, row by row, width * height of them). */
	public abstract void uploadTexture(Object texture, int[] argb);

	public abstract void destroyTexture(Object texture);

	public abstract String userName();

	public abstract UUID userId();

	/** Swaps the signed-in session for the one from the launcher. Throws with a readable message on failure. */
	public abstract void switchSession(LauncherBridge.Session session) throws Exception;

	// ---- Polar cosmetics and friends

	/** The ids of the players in the current world (the ones the game knows about, near you). Empty outside a world. */
	public abstract java.util.List<UUID> worldPlayerIds();

	/** Shows a line in chat for you only (not sent anywhere). Formatting codes (section sign) allowed. */
	public abstract void chatLine(String text);

	/** Leaves the current world or server (if any) and joins this server address. */
	public abstract void joinServer(String address);

	/** The key a Polar key bind is set to, as shown in Controls (e.g. "Y"), or null when it isn't bound. */
	public abstract String polarKeyName(PolarKey key);
}
