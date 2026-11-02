package dev.polarclient.modules.world;

import dev.polarclient.Polar;
import dev.polarclient.gfx.Gfx;
import dev.polarclient.gui.WaypointsView;
import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.platform.PolarKey;
import dev.polarclient.setting.ActionSetting;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.NumberSetting;
import dev.polarclient.util.Draw;
import dev.polarclient.util.Popups;
import dev.polarclient.world.Waypoint;
import dev.polarclient.world.WaypointStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Named spots shown in the world with a light beam, a label and the distance. */
public class WaypointsModule extends Module {
	private static final int SAMPLES = 28;
	public final ActionSetting addHere = add(new ActionSetting("add_here", "Add waypoint here", "Or press the \"Add Waypoint Here\" key (B).", "Add", this::addHere));
	public final ActionSetting manage = add(new ActionSetting("manage", "Manage waypoints", "Rename, recolour, hide or delete waypoints.", "Open", () -> p().open(new WaypointsView(p().currentScreen()))));
	public final BooleanSetting beams = add(new BooleanSetting("beams", "Light beams", true));
	public final NumberSetting beamHeight = add(new NumberSetting("beam_height", "Beam height", 160, 16, 384, 16, " blocks"));
	public final BooleanSetting labels = add(new BooleanSetting("labels", "Names", true));
	public final BooleanSetting distance = add(new BooleanSetting("distance", "Distance", true));
	public final NumberSetting maxDistance = add(new NumberSetting("max_distance", "Hide beyond (0 = never)", 0, 0, 10000, 100, " m"));
	public final BooleanSetting showDeath = add(new BooleanSetting("show_death", "Show last death", true));

	public WaypointsModule() {
		super("waypoints", "Waypoints", "Mark spots in the world with beams you can see from far away. Press B to add one.", Category.WORLD, true);
		beamHeight.visibleWhen(beams::on);
	}

	@Override
	public void tick() {
		while (p().consumePolarKey(PolarKey.ADD_WAYPOINT)) {
			if (isEnabled() && p().hasPlayer()) addHere();
		}
		while (p().consumePolarKey(PolarKey.WAYPOINTS)) {
			if (isEnabled() && !p().screenOpen()) p().open(new WaypointsView(null));
		}
	}

	/** A new waypoint at the player's feet, named "Waypoint N", in the next colour. */
	public void addHere() {
		String world = p().worldId();
		if (world == null || !p().hasPlayer()) {
			Popups.show("Join a world to add waypoints", 0xFFFF6B6B, 2500);
			return;
		}
		List<Waypoint> list = WaypointStore.of(world);
		if (list.size() >= WaypointStore.MAX_PER_WORLD) {
			Popups.show("This world already has " + WaypointStore.MAX_PER_WORLD + " waypoints", 0xFFFF6B6B, 2500);
			return;
		}
		int n = list.size() + 1;
		int color = WaypointsView.COLORS[(n - 1) % WaypointsView.COLORS.length];
		Waypoint w = new Waypoint("Waypoint " + n, (int) Math.floor(p().playerX()), (int) Math.floor(p().playerY()), (int) Math.floor(p().playerZ()), p().dimension(), color);
		list.add(w);
		WaypointStore.save();
		Popups.show("Added " + w.name + " at " + w.x + ", " + w.y + ", " + w.z, color, 2500);
	}

	private static final class Marker {
		final String name;
		final double x, y, z, dist;
		final int color;

		Marker(String name, double x, double y, double z, int color, double dist) {
			this.name = name;
			this.x = x;
			this.y = y;
			this.z = z;
			this.color = color;
			this.dist = dist;
		}
	}

	@Override
	public void renderOverlay(Gfx g) {
		if (!p().inWorld() || !p().hasPlayer()) return;
		String world = p().worldId(), dim = p().dimension();
		double px = p().playerX(), py = p().playerY(), pz = p().playerZ();
		List<Marker> markers = new ArrayList<Marker>();
		for (Waypoint w : WaypointStore.of(world)) {
			if (!w.visible || dim == null || !dim.equals(w.dim)) continue;
			markers.add(new Marker(w.name, w.x + 0.5, w.y, w.z + 0.5, w.color, w.distanceTo(px, py, pz)));
		}
		if (showDeath.on()) {
			DeathWaypointModule death = (DeathWaypointModule) Polar.modules().byId("death_waypoint");
			double[] d = death == null ? null : death.lastDeath();
			if (d != null && dim != null && dim.equals(death.lastDeathDimension())) {
				double dist = Math.sqrt((d[0] - px) * (d[0] - px) + (d[1] - py) * (d[1] - py) + (d[2] - pz) * (d[2] - pz));
				markers.add(new Marker("Last death", Math.floor(d[0]) + 0.5, Math.floor(d[1]), Math.floor(d[2]) + 0.5, 0xFFFF6B6B, dist));
			}
		}
		double max = maxDistance.get();
		Collections.sort(markers, (a, b) -> Double.compare(b.dist, a.dist));
		for (Marker m : markers) {
			if (max > 0 && m.dist > max) continue;
			if (beams.on()) drawBeam(g, m);
			drawLabel(g, m);
		}
	}

	private void drawBeam(Gfx g, Marker m) {
		double top = m.y + beamHeight.get();
		float[] prev = null;
		for (int i = 0; i <= SAMPLES; i++) {
			double y = m.y + (top - m.y) * i / SAMPLES;
			float[] cur = p().project(m.x, y, m.z);
			if (prev != null && cur != null) {
				float depth = Math.max(0.5f, (prev[2] + cur[2]) / 2f);
				float w = Math.max(1f, Math.min(7f, 0.3f * 0.9f * g.height() / depth));
				float fade = 1f - i / (float) SAMPLES;
				int color = Draw.alpha(m.color, Math.round(150 * fade * fade + 20));
				int x0 = Math.round((prev[0] + cur[0]) / 2f - w / 2f);
				int y0 = Math.round(Math.min(prev[1], cur[1])), y1 = Math.round(Math.max(prev[1], cur[1]));
				if (y1 > y0) g.fill(x0, y0, Math.round(x0 + w), y1, color);
			}
			prev = cur;
		}
	}

	private void drawLabel(Gfx g, Marker m) {
		float[] s = p().project(m.x, m.y + 1.0, m.z);
		if (s == null || s[0] < -50 || s[0] > g.width() + 50 || s[1] < -20 || s[1] > g.height() + 20) return;
		int x = Math.round(s[0]), y = Math.round(s[1]);
		// diamond marker
		for (int r = 0; r < 4; r++) g.fill(x - r, y - 4 + r, x + r + 1, y - 3 + r, m.color);
		for (int r = 0; r < 4; r++) g.fill(x - 3 + r, y + r, x + 4 - r, y + r + 1, m.color);
		String text = (labels.on() ? m.name : "") + (distance.on() ? (labels.on() ? "  " : "") + formatDistance(m.dist) : "");
		if (text.isEmpty()) return;
		int tw = Draw.width(text);
		int bx = x - tw / 2 - 4, by = y - 18;
		Draw.round(g, bx, by, tw + 8, 12, 3, 0xB00B1426);
		g.fill(bx + 2, by + 11, bx + tw + 6, by + 12, m.color);
		g.text(text, bx + 4, by + 2, 0xFFFFFFFF, true);
	}

	public static String formatDistance(double d) {
		return d >= 1000 ? String.format(Locale.ROOT, "%.1fkm", d / 1000) : Math.round(d) + "m";
	}
}
