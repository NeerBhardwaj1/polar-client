package dev.polarclient.modules.perf;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ModeSetting;
import dev.polarclient.setting.NumberSetting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Less lag in mending sessions, when lots of Bottles o' Enchanting are thrown. Throwing works exactly as normal; only
 * what's drawn and heard changes:
 * <ul>
 * <li>each bottle that breaks makes about 100 particles; only some of them are shown (other potions' splashes keep
 * all of theirs);</li>
 * <li>XP orbs in the same block are drawn as one, with the game's own picture for their combined XP (you still pick up
 * every point);</li>
 * <li>the orb pickup "ding" can play less often, or not at all.</li>
 * </ul>
 * Each version tells it when a bottle's splash starts and ends ({@link #splashStart}), asks it about particles
 * ({@link #dropParticle}), orbs ({@link #hidesOrb}, {@link #orbIcon}) and sounds ({@link #allowSound}).
 */
public class XpBottleOptimizerModule extends Module {
	/** The colour the game sends with an XP bottle's splash (a water bottle's). 1.8.9 sends 0 instead. */
	public static final int XP_SPLASH_COLOR = 0x385DC6;
	/** "Fewer" pickup sounds: at most one this often. */
	private static final long SOUND_GAP_NANOS = 80_000_000L;

	public final NumberSetting splash = add(new NumberSetting("splash", "Splash particles", 20, 0, 100, 5, "%"));
	public final BooleanSetting merge = add(new BooleanSetting("merge", "Merge XP orbs", true));
	public final ModeSetting sounds = add(new ModeSetting("sounds", "Pickup sounds", "Fewer", "Normal", "Fewer", "Off"));

	private final Random random = new Random();
	private boolean inSplash;
	private long lastSound;
	// orb merging, worked out again every tick: the first orb found in each block stands for all of them
	private final Map<Long, Integer> leaderOfCell = new HashMap<Long, Integer>();
	private final Map<Integer, Integer> total = new HashMap<Integer, Integer>();
	private final Map<Integer, Integer> merged = new HashMap<Integer, Integer>();
	private final Set<Integer> hidden = new HashSet<Integer>();

	public XpBottleOptimizerModule() {
		super("xp_bottle_optimizer", "XP Bottle Optimizer", "Less lag when throwing lots of XP bottles.", Category.PERFORMANCE, false);
	}

	/** A potion splash (the game's level event 2002) starts; xpBottle: it's from a Bottle o' Enchanting. */
	public void splashStart(boolean xpBottle) {
		inSplash = xpBottle && isEnabled();
	}

	public void splashEnd() {
		inSplash = false;
	}

	/** Whether to drop a particle the game is adding now (only during an XP bottle's splash). */
	public boolean dropParticle() {
		if (!inSplash) return false;
		double keep = splash.get();
		return keep < 100 && (keep <= 0 || random.nextDouble() * 100 >= keep);
	}

	/** Whether a sound may play; id: the sound's name, e.g. "minecraft:entity.experience_orb.pickup". */
	public boolean allowSound(String id) {
		if (!isEnabled() || id == null || !(id.endsWith("entity.experience_orb.pickup") || id.endsWith("random.orb"))) return true;
		if (sounds.is("Off")) return false;
		if (sounds.is("Normal")) return true;
		long now = System.nanoTime();
		if (now - lastSound < SOUND_GAP_NANOS) return false;
		lastSound = now;
		return true;
	}

	@Override
	public void tick() {
		leaderOfCell.clear();
		total.clear();
		merged.clear();
		hidden.clear();
		if (!isEnabled() || !merge.on() || p() == null || !p().hasPlayer()) return;
		p().forEachXpOrb(this::orb);
		for (Map.Entry<Integer, Integer> e : total.entrySet()) {
			if (e.getValue() > 0) merged.put(e.getKey(), e.getValue());
		}
	}

	private void orb(int id, double x, double y, double z, int value) {
		long cell = (((long) Math.floor(x) & 0x3FFFFFL) << 42) | (((long) Math.floor(y) & 0xFFFFFL) << 22) | ((long) Math.floor(z) & 0x3FFFFFL);
		Integer leader = leaderOfCell.get(cell);
		if (leader == null) {
			leaderOfCell.put(cell, id);
			total.put(id, -value); // negative until a second orb joins
			return;
		}
		hidden.add(id);
		int t = total.get(leader);
		total.put(leader, Math.abs(t) + value);
	}

	/** Whether an XP orb isn't drawn (another orb in its block stands for it). */
	public boolean hidesOrb(int entityId) {
		return !hidden.isEmpty() && hidden.contains(entityId);
	}

	/** The picture (0-10, bigger = more XP) to draw an orb with: for an orb standing for others, their combined XP's. */
	public int orbIcon(int entityId, int icon) {
		if (merged.isEmpty()) return icon;
		Integer sum = merged.get(entityId);
		return sum == null ? icon : Math.max(icon, iconFor(sum));
	}

	/** The game's own orb picture for an amount of XP. */
	public static int iconFor(int value) {
		if (value >= 2477) return 10;
		if (value >= 1237) return 9;
		if (value >= 617) return 8;
		if (value >= 307) return 7;
		if (value >= 149) return 6;
		if (value >= 73) return 5;
		if (value >= 37) return 4;
		if (value >= 17) return 3;
		if (value >= 7) return 2;
		if (value >= 3) return 1;
		return 0;
	}
}
