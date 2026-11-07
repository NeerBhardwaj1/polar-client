package dev.polarclient.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.polarclient.Log;
import dev.polarclient.Polar;
import dev.polarclient.account.LauncherBridge;
import dev.polarclient.platform.Platform;
import dev.polarclient.platform.PolarKey;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Polar cosmetics in game: who around you is on Polar, what they wear, and the files to draw it. Everything goes
 * through the Polar launcher (it signs in to the Polar server and keeps the files), so the game never talks to the
 * internet for this. Each Minecraft version asks this class while drawing players: {@link #capeTexture},
 * {@link #models}, {@link #aura} and {@link #badge}.
 */
public final class Cosmetics {
	private static final long STATE_EVERY = 30000, LOOKUP_EVERY = 1000, LOOK_TTL = 60000, EVENTS_EVERY = 3000, FORGET_AFTER = 5000;
	private static final int CAPE_W = 256, CAPE_H = 128;
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Polar Cosmetics");
		t.setDaemon(true);
		return t;
	});

	/** What a player wears: slot -> item id and colour. */
	static final class Look {
		final boolean badge;
		final Map<String, String> items = new HashMap<>();
		final Map<String, Integer> colors = new HashMap<>();
		final long at = System.currentTimeMillis();

		Look(boolean badge) {
			this.badge = badge;
		}
	}

	/** A catalogue entry, as far as the game needs it. */
	static final class Item {
		String id, type, model, texture;
		int frames = 1, frameTime = 100, defaultColor = 0xFFFFFFFF;
		JsonObject particles;
	}

	/** Downloaded and decoded files for one item, then its game textures (made on the render thread). */
	static final class Asset {
		volatile boolean ready, failed;
		CosmeticModel model;
		Aura.Settings aura;
		int w, h, frames = 1, frameTime = 100;
		int[][] pixels, glowPixels;
		Object texture, glowTexture;
		int uploaded = -1;
		/** Wings only: particles that drift off the wings (the same rules as auras). */
		Aura.Settings trail;
		int trailW, trailH;
		int[] trailPixels;
		Object trailTexture;
	}

	/** Per-player drawing state: animation clocks and aura particles (render thread only). */
	private static final class Fx {
		final Map<String, Float> phases = new HashMap<>();
		final Aura aura = new Aura();
		final Aura trail = new Aura();
		final CosmeticModel.Motion motion = new CosmeticModel.Motion();
		String auraId, trailId;
		long head, body, auraAt, trailAt, seen;
		/** The wings' pose, eased towards what the player is doing (0..1 each), and how fast the body turns. */
		float sprint, fold, glide, fly, yawSpeed, lastYaw;
		boolean hasYaw;
	}

	private static volatile boolean signedIn;
	private static volatile Map<String, Item> catalog;
	private static volatile boolean catalogLoading;
	private static final Map<String, Look> LOOKS = new ConcurrentHashMap<>();
	private static final Map<String, Long> ASKED = new ConcurrentHashMap<>();
	private static final Map<String, Asset> ASSETS = new ConcurrentHashMap<>();
	private static final Map<String, Fx> FX = new HashMap<>();
	private static long nextState, nextLookup, nextEvents;
	private static volatile boolean stateBusy, lookupBusy, eventsBusy;
	private static volatile String inviteServer, inviteFrom;
	private static volatile long inviteAt;

	private Cosmetics() {
	}

	// ---------------------------------------------------------------- the settings (Polar menu › Polar Cosmetics)

	private static PolarCosmeticsModule module() {
		return Polar.modules() == null ? null : Polar.modules().cosmetics;
	}

	private static boolean on() {
		PolarCosmeticsModule m = module();
		return signedIn && catalog != null && m != null && m.isEnabled();
	}

	private static boolean shows(String uuid) {
		PolarCosmeticsModule m = module();
		if (m == null) return false;
		UUID me = Platform.get() == null ? null : Platform.get().userId();
		return me != null && me.toString().equals(uuid) ? m.showOwn() : m.showOthers();
	}

	// ---------------------------------------------------------------- every client tick

	public static void tick() {
		Platform p = Platform.get();
		if (p == null || !LauncherBridge.available()) return;
		long now = System.currentTimeMillis();
		if (now >= nextState && !stateBusy) {
			nextState = now + STATE_EVERY;
			stateBusy = true;
			IO.execute(Cosmetics::refreshState);
		}
		while (p.consumePolarKey(PolarKey.JOIN_INVITE)) joinInvite();
		if (!signedIn) return;
		if (now >= nextEvents && !eventsBusy) {
			nextEvents = now + EVENTS_EVERY;
			eventsBusy = true;
			IO.execute(Cosmetics::pollEvents);
		}
		if (!on() || !p.inWorld()) return;
		if (now >= nextLookup && !lookupBusy) {
			nextLookup = now + LOOKUP_EVERY;
			List<String> ask = new ArrayList<>();
			for (UUID id : p.worldPlayerIds()) {
				String key = id.toString();
				Long at = ASKED.get(key);
				if (at == null || now - at > LOOK_TTL) {
					ask.add(key);
					ASKED.put(key, now);
					if (ask.size() >= 200) break;
				}
			}
			if (!ask.isEmpty()) {
				lookupBusy = true;
				IO.execute(() -> lookup(ask));
			}
		}
	}

	private static void refreshState() {
		try {
			JsonObject o = LauncherBridge.polar("GET", "/polar/state", null, 5000);
			signedIn = o.has("signedIn") && o.get("signedIn").getAsBoolean();
			if (signedIn && catalog == null && !catalogLoading) loadCatalog();
		} catch (RuntimeException e) {
			signedIn = false;
		} finally {
			stateBusy = false;
		}
	}

	@SuppressWarnings("deprecation")
	private static void loadCatalog() {
		catalogLoading = true;
		try {
			JsonObject o = new JsonParser().parse(new String(LauncherBridge.polarFile("catalog.json", 15000), StandardCharsets.UTF_8)).getAsJsonObject();
			Map<String, Item> items = new HashMap<>();
			for (JsonElement e : o.getAsJsonArray("items")) {
				JsonObject j = e.getAsJsonObject();
				Item it = new Item();
				it.id = str(j, "id");
				it.type = str(j, "type");
				it.model = j.has("model") ? str(j, "model") : null;
				it.texture = j.has("texture") ? str(j, "texture") : null;
				it.frames = j.has("frames") ? j.get("frames").getAsInt() : 1;
				it.frameTime = j.has("frameTime") ? j.get("frameTime").getAsInt() : 100;
				if (j.has("defaultColor")) it.defaultColor = parseColor(str(j, "defaultColor"));
				it.particles = j.has("particles") ? j.getAsJsonObject("particles") : null;
				items.put(it.id, it);
			}
			catalog = Collections.unmodifiableMap(items);
		} catch (RuntimeException e) {
			Log.error("Couldn't load the Polar cosmetics catalogue", e);
		} finally {
			catalogLoading = false;
		}
	}

	private static void lookup(List<String> uuids) {
		try {
			JsonObject body = new JsonObject();
			JsonArray arr = new JsonArray();
			for (String u : uuids) arr.add(new com.google.gson.JsonPrimitive(u));
			body.add("uuids", arr);
			JsonObject o = LauncherBridge.polar("POST", "/polar/players", body.toString(), 15000);
			JsonObject players = o.has("players") ? o.getAsJsonObject("players") : new JsonObject();
			for (String u : uuids) {
				JsonElement e = players.get(u);
				if (e == null || !e.isJsonObject()) {
					LOOKS.remove(u);
					continue;
				}
				JsonObject j = e.getAsJsonObject();
				Look look = new Look(!j.has("badge") || j.get("badge").getAsBoolean());
				if (j.has("slots") && j.get("slots").isJsonObject()) {
					for (Map.Entry<String, JsonElement> s : j.getAsJsonObject("slots").entrySet()) {
						if (!s.getValue().isJsonObject()) continue;
						JsonObject slot = s.getValue().getAsJsonObject();
						String id = str(slot, "id");
						Item it = catalog == null ? null : catalog.get(id);
						if (it == null) continue;
						look.items.put(s.getKey(), id);
						look.colors.put(s.getKey(), slot.has("color") ? parseColor(str(slot, "color")) : it.defaultColor);
						load(it);
					}
				}
				LOOKS.put(u, look);
			}
		} catch (RuntimeException e) {
			// the launcher went away or isn't signed in: try again on the next round
			for (String u : uuids) ASKED.remove(u);
		} finally {
			lookupBusy = false;
		}
	}

	/** Downloads and decodes an item's files in the background (once). */
	private static void load(Item it) {
		if (ASSETS.containsKey(it.id)) return;
		Asset a = new Asset();
		ASSETS.put(it.id, a);
		try {
			if ("cape".equals(it.type)) {
				a.frames = Math.max(1, it.frames);
				a.frameTime = Math.max(16, it.frameTime);
				a.w = CAPE_W;
				a.h = CAPE_H;
				a.pixels = frames(LauncherBridge.polarFile(it.texture, 30000), a.frames, a.w, a.h);
			} else if ("aura".equals(it.type)) {
				a.aura = new Aura.Settings(it.particles);
				BufferedImage img = image(LauncherBridge.polarFile(str(it.particles, "texture"), 30000));
				a.w = img.getWidth();
				a.h = img.getHeight();
				a.pixels = new int[][] {img.getRGB(0, 0, a.w, a.h, null, 0, a.w)};
			} else if (it.model != null) {
				@SuppressWarnings("deprecation")
				JsonObject m = new JsonParser().parse(new String(LauncherBridge.polarFile(it.model, 30000), StandardCharsets.UTF_8)).getAsJsonObject();
				a.model = new CosmeticModel(m);
				a.frames = a.model.frames;
				a.frameTime = a.model.frameTime;
				a.w = a.model.texW;
				a.h = a.model.texH;
				a.pixels = frames(LauncherBridge.polarFile(str(m, "texture"), 30000), a.frames, a.w, a.h);
				if (a.model.glow) a.glowPixels = frames(LauncherBridge.polarFile(str(m, "glow"), 30000), a.frames, a.w, a.h);
				if ("wings".equals(it.type) && it.particles != null && it.particles.has("texture")) {
					a.trail = new Aura.Settings(it.particles);
					BufferedImage img = image(LauncherBridge.polarFile(str(it.particles, "texture"), 30000));
					a.trailW = img.getWidth();
					a.trailH = img.getHeight();
					a.trailPixels = img.getRGB(0, 0, a.trailW, a.trailH, null, 0, a.trailW);
				}
			} else {
				a.failed = true;
				return;
			}
			a.ready = true;
		} catch (RuntimeException e) {
			a.failed = true;
			Log.error("Couldn't load the Polar cosmetic " + it.id, e);
		}
	}

	private static BufferedImage image(byte[] png) {
		try {
			BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
			if (img == null) throw new IllegalStateException("not an image");
			return img;
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Splits a strip of frames (stacked top to bottom) into ARGB pixel arrays of w x h. */
	private static int[][] frames(byte[] png, int frames, int w, int h) {
		BufferedImage img = image(png);
		int fh = img.getHeight() / frames;
		int[][] out = new int[frames][];
		for (int f = 0; f < frames; f++) {
			int[] px = img.getRGB(0, f * fh, Math.min(w, img.getWidth()), Math.min(h, fh), null, 0, Math.min(w, img.getWidth()));
			if (img.getWidth() != w || fh != h) { // not the expected size: copy into place
				int[] fit = new int[w * h];
				int sw = Math.min(w, img.getWidth()), sh = Math.min(h, fh);
				for (int y = 0; y < sh; y++) System.arraycopy(px, y * sw, fit, y * w, sw);
				px = fit;
			}
			out[f] = px;
		}
		return out;
	}

	// ---------------------------------------------------------------- friends: invites and requests

	private static void pollEvents() {
		try {
			JsonObject o = LauncherBridge.polar("GET", "/polar/events", null, 5000);
			if (!o.has("events")) return;
			for (JsonElement e : o.getAsJsonArray("events")) {
				if (!e.isJsonObject()) continue;
				JsonObject ev = e.getAsJsonObject();
				String type = str(ev, "type");
				String from = ev.has("from") && ev.get("from").isJsonObject() ? str(ev.getAsJsonObject("from"), "name") : "A friend";
				if ("invite".equals(type)) {
					inviteServer = str(ev, "server");
					inviteFrom = from;
					inviteAt = System.currentTimeMillis();
					String key = Platform.get().polarKeyName(PolarKey.JOIN_INVITE);
					say("§b" + from + "§r invited you to §e" + inviteServer + "§r." + (key == null ? " Join it from the Polar launcher." : " Press §a" + key + "§r to join."));
				} else if ("friends".equals(type)) {
					String ev2 = str(ev, "event");
					if ("request".equals(ev2)) say("§b" + from + "§r wants to be your friend. Accept it in the Polar launcher (Friends).");
					else if ("accepted".equals(ev2)) say("§b" + from + "§r accepted your friend request.");
				}
			}
		} catch (RuntimeException ignored) {
			// try again next time
		} finally {
			eventsBusy = false;
		}
	}

	private static void say(final String text) {
		final Platform p = Platform.get();
		if (p != null) p.execute(() -> p.chatLine("§3[Polar]§r " + text));
	}

	private static void joinInvite() {
		String server = inviteServer;
		if (server == null || server.isEmpty() || System.currentTimeMillis() - inviteAt > 5 * 60000) return;
		inviteServer = null;
		Platform p = Platform.get();
		if (p.inWorld() && server.equalsIgnoreCase(String.valueOf(p.serverAddress()))) return;
		p.joinServer(server);
	}

	// ---------------------------------------------------------------- drawing (render thread)

	private static Look look(UUID uuid) {
		if (uuid == null || !on()) return null;
		String key = uuid.toString();
		if (!shows(key)) return null;
		return LOOKS.get(key);
	}

	private static Asset asset(Look look, String slot) {
		String id = look.items.get(slot);
		if (id == null) return null;
		Asset a = ASSETS.get(id);
		return a != null && a.ready ? a : null;
	}

	/** Whether the Polar badge goes next to this player's name. */
	public static boolean badge(UUID uuid) {
		PolarCosmeticsModule m = module();
		if (uuid == null || m == null || !m.isEnabled() || !m.showBadges() || !signedIn) return false;
		Look l = LOOKS.get(uuid.toString());
		return l != null && l.badge;
	}

	/** The texture to use as this player's cape (the game's own cape is replaced), or null. */
	public static Object capeTexture(UUID uuid) {
		try {
			return capeTexture0(uuid);
		} catch (RuntimeException e) {
			failed(e);
			return null;
		}
	}

	private static Object capeTexture0(UUID uuid) {
		Look l = look(uuid);
		Asset a = l == null ? null : asset(l, "cape");
		return a == null ? null : texture(a, "cape");
	}

	/**
	 * The model cosmetics on the head (head = true) or body: quads in the space of that body part (after its own
	 * pivot and rotation, as Minecraft's model parts are drawn), one batch per texture and kind of drawing.
	 * moving: 0 standing .. 1 walking or running.
	 */
	public static List<RenderBatch> models(UUID uuid, boolean head, float moving) {
		return models(uuid, head, WingPose.walking(moving));
	}

	/** The same, with what the player is doing, so wings can react (sprint, sneak, glide, fly, turn). */
	public static List<RenderBatch> models(UUID uuid, boolean head, WingPose pose) {
		try {
			return models0(uuid, head, pose == null ? WingPose.walking(0) : pose);
		} catch (RuntimeException e) {
			failed(e);
			return Collections.emptyList();
		}
	}

	/** Beyond this distance (blocks) other players' wings hold still. */
	private static final double STILL_BEYOND = 32;

	private static List<RenderBatch> models0(UUID uuid, boolean head, WingPose pose) {
		Look l = look(uuid);
		if (l == null) return Collections.emptyList();
		Fx fx = fx(uuid);
		long now = System.currentTimeMillis();
		long last = head ? fx.head : fx.body;
		float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1000f);
		if (head) fx.head = now;
		else fx.body = now;
		float moving = Math.max(0, Math.min(1, pose.moving));
		List<RenderBatch> out = new ArrayList<>(4);
		for (String slot : new String[] {"wings", "head", "back"}) {
			Asset a = asset(l, slot);
			if (a == null || a.model == null || a.model.head != head) continue;
			boolean wings = "wings".equals(slot);
			if (wings && pose.elytra) continue;
			Object tex = texture(a, slot);
			if (tex == null) continue;
			RenderBatch base = new RenderBatch(tex, a.model.translucent ? RenderBatch.TRANSLUCENT : RenderBatch.CUTOUT);
			RenderBatch glow = a.glowTexture != null ? new RenderBatch(a.glowTexture, RenderBatch.GLOW) : null;
			Integer c = l.colors.get(slot);
			CosmeticModel.Motion motion = CosmeticModel.Motion.NONE;
			float slotDt = dt, slotMoving = moving;
			if (wings) {
				motion = wingMotion(fx, pose, dt);
				if (pose.flying || pose.gliding) slotMoving = 1;
				if (pose.distanceSq > STILL_BEYOND * STILL_BEYOND) slotDt = 0;
			}
			a.model.emit(fx.phases, slotDt, slotMoving, now, c == null ? 0xFFFFFFFF : c, base, glow, motion);
			out.add(base);
			if (glow != null && glow.count > 0) out.add(glow);
		}
		return out;
	}

	/** How quickly the wings follow a change of what the player is doing (seconds). */
	private static final float EASE = 0.18f, TURN_EASE = 0.12f;

	/**
	 * Works out this frame's wing motion for a player: eases towards the pose that fits what they are doing, so a
	 * sneak folds the wings in over a fifth of a second instead of snapping.
	 */
	private static CosmeticModel.Motion wingMotion(Fx fx, WingPose p, float dt) {
		PolarCosmeticsModule m = module();
		float k = dt <= 0 ? 0 : 1 - (float) Math.exp(-dt / EASE);
		boolean tucked = p.sneaking || p.swimming || p.riding || p.sleeping;
		boolean flying = p.flying || p.gliding;
		boolean falling = !p.onGround && !flying && !p.swimming && !p.riding;
		fx.sprint += ((p.sprinting && !tucked ? 1 : 0) - fx.sprint) * k;
		fx.fold += ((tucked ? 1 : 0) - fx.fold) * k;
		fx.glide += ((falling ? 1 : 0) - fx.glide) * k;
		fx.fly += ((flying ? 1 : 0) - fx.fly) * k;
		if (fx.hasYaw && dt > 0) {
			float turn = p.bodyYaw - fx.lastYaw;
			turn -= 360 * Math.round(turn / 360);
			float speed = Math.max(-720, Math.min(720, turn / dt));
			fx.yawSpeed += (speed - fx.yawSpeed) * (1 - (float) Math.exp(-dt / TURN_EASE));
		}
		fx.lastYaw = p.bodyYaw;
		fx.hasYaw = true;
		CosmeticModel.Motion mo = fx.motion;
		mo.amp = (1 + 0.3f * fx.sprint + 0.45f * fx.fly) * (1 - 0.8f * fx.fold) * (1 - 0.5f * fx.glide);
		mo.speed = (1 + 0.35f * fx.sprint + 0.6f * fx.fly) * (1 - 0.35f * fx.fold) * (1 - 0.3f * fx.glide);
		mo.fold = fx.fold;
		mo.glide = fx.glide * (1 - fx.fly);
		// a turn of the body leaves the wings trailing a little behind it
		mo.lag = Math.max(-14, Math.min(14, -fx.yawSpeed * 0.05f));
		mo.scale = m == null ? 1 : m.wingScale();
		if (m != null && !m.animateWings()) {
			mo.amp = 0;
			mo.lag = 0;
		}
		return mo;
	}

	/**
	 * Aura sprites around this player, relative to (x, y, z): the player's position as drawn this frame (feet). The
	 * renderer draws them from there without the player's turning. camYaw/camPitch: the camera's angles in degrees.
	 */
	public static List<RenderBatch> aura(UUID uuid, double x, double y, double z, float camYaw, float camPitch) {
		try {
			return aura0(uuid, x, y, z, camYaw, camPitch);
		} catch (RuntimeException e) {
			failed(e);
			return Collections.emptyList();
		}
	}

	private static List<RenderBatch> aura0(UUID uuid, double x, double y, double z, float camYaw, float camPitch) {
		Look l = look(uuid);
		PolarCosmeticsModule m = module();
		if (l == null || m == null) return Collections.emptyList();
		List<RenderBatch> trail = trail(l, m, uuid, x, y, z, camYaw, camPitch);
		List<RenderBatch> aura = m.showAuras() ? auraOnly(l, uuid, x, y, z, camYaw, camPitch) : Collections.<RenderBatch>emptyList();
		if (trail.isEmpty()) return aura;
		if (aura.isEmpty()) return trail;
		List<RenderBatch> both = new ArrayList<>(aura);
		both.addAll(trail);
		return both;
	}

	/** Particles that drift off a player's wings (only wings that have them, and only with Wing particles on). */
	private static List<RenderBatch> trail(Look l, PolarCosmeticsModule m, UUID uuid, double x, double y, double z, float camYaw, float camPitch) {
		if (!m.wingParticles()) return Collections.emptyList();
		Asset a = asset(l, "wings");
		if (a == null || a.trail == null || a.trailPixels == null) return Collections.emptyList();
		Platform p = Platform.get();
		if (p == null) return Collections.emptyList();
		if (a.trailTexture == null) {
			a.trailTexture = p.createTexture("cosmetic_trail", a.trailW, a.trailH);
			p.uploadTexture(a.trailTexture, a.trailPixels);
		}
		Fx fx = fx(uuid);
		String id = l.items.get("wings");
		if (!id.equals(fx.trailId)) {
			fx.trailId = id;
			fx.trailAt = 0;
		}
		long now = System.nanoTime();
		float dt = fx.trailAt == 0 ? 0 : Math.min(0.1f, (now - fx.trailAt) / 1e9f);
		fx.trailAt = now;
		fx.trail.step(a.trail, dt, x, y, z);
		RenderBatch b = new RenderBatch(a.trailTexture, a.trail.glow ? RenderBatch.GLOW : RenderBatch.TRANSLUCENT_BOTH);
		fx.trail.emit(a.trail, x, y, z, camYaw, camPitch, b);
		return b.count == 0 ? Collections.<RenderBatch>emptyList() : Collections.singletonList(b);
	}

	private static List<RenderBatch> auraOnly(Look l, UUID uuid, double x, double y, double z, float camYaw, float camPitch) {
		Asset a = asset(l, "aura");
		if (a == null || a.aura == null) return Collections.emptyList();
		Object tex = texture(a, "aura");
		if (tex == null) return Collections.emptyList();
		Fx fx = fx(uuid);
		String id = l.items.get("aura");
		if (!id.equals(fx.auraId)) {
			fx.auraId = id;
			fx.auraAt = 0;
		}
		long now = System.nanoTime();
		float dt = fx.auraAt == 0 ? 0 : Math.min(0.1f, (now - fx.auraAt) / 1e9f);
		fx.auraAt = now;
		fx.aura.step(a.aura, dt, x, y, z);
		RenderBatch b = new RenderBatch(tex, a.aura.glow ? RenderBatch.GLOW : RenderBatch.TRANSLUCENT_BOTH);
		fx.aura.emit(a.aura, x, y, z, camYaw, camPitch, b);
		return b.count == 0 ? Collections.<RenderBatch>emptyList() : Collections.singletonList(b);
	}

	private static Fx fx(UUID uuid) {
		String key = uuid.toString();
		Fx fx = FX.get(key);
		long now = System.currentTimeMillis();
		if (fx == null) {
			if (FX.size() > 200) forget(now);
			FX.put(key, fx = new Fx());
		}
		fx.seen = now;
		return fx;
	}

	private static void forget(long now) {
		for (Iterator<Map.Entry<String, Fx>> it = FX.entrySet().iterator(); it.hasNext();) {
			if (now - it.next().getValue().seen > FORGET_AFTER) it.remove();
		}
	}

	/** The game texture for an asset, made on first use; animated ones get the current frame uploaded. */
	private static Object texture(Asset a, String kind) {
		Platform p = Platform.get();
		if (p == null || a.pixels == null) return null;
		if (a.texture == null) {
			a.texture = p.createTexture("cosmetic_" + kind, a.w, a.h);
			if (a.glowPixels != null) a.glowTexture = p.createTexture("cosmetic_glow", a.w, a.h);
		}
		int frame = a.frames > 1 ? (int) ((System.currentTimeMillis() / a.frameTime) % a.frames) : 0;
		if (frame != a.uploaded) {
			a.uploaded = frame;
			p.uploadTexture(a.texture, a.pixels[frame]);
			if (a.glowTexture != null) p.uploadTexture(a.glowTexture, a.glowPixels[frame]);
		}
		return a.texture;
	}

	private static boolean loggedFailure;

	/** Drawing a cosmetic went wrong: never break the game's rendering for it; say so once in the log. */
	private static void failed(RuntimeException e) {
		if (loggedFailure) return;
		loggedFailure = true;
		Log.error("Polar cosmetics: couldn't draw a cosmetic (skipping it)", e);
	}

	/** Forgets everyone (another account, or the launcher signed out). */
	public static void reset() {
		LOOKS.clear();
		ASKED.clear();
	}

	static int parseColor(String hex) {
		String h = hex == null ? "" : hex.trim().toLowerCase(Locale.ROOT);
		if (h.startsWith("#")) h = h.substring(1);
		if (!h.matches("[0-9a-f]{6}")) return 0;
		return 0xFF000000 | Integer.parseInt(h, 16);
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}
}
