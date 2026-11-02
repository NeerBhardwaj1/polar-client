package dev.polarclient.modules.world;

import dev.polarclient.Polar;
import dev.polarclient.gfx.Gfx;
import dev.polarclient.gfx.UiFont;
import dev.polarclient.gui.ui.Icon;
import dev.polarclient.gui.ui.Ui;
import dev.polarclient.module.Category;
import dev.polarclient.module.HudModule;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.NumberSetting;
import dev.polarclient.util.Draw;
import dev.polarclient.world.MinimapImage;
import dev.polarclient.world.Waypoint;
import dev.polarclient.world.WaypointStore;

import java.util.Locale;

/**
 * A light minimap: the ground around you coloured like a vanilla map, your Polar waypoints and last death on it, north
 * up or turning with you. In the Nether it shows the floor at your height instead of the roof. No entity radar.
 */
public class MinimapModule extends HudModule {
	private static final int BACKGROUND = 0xCC0B1426, BORDER = 0xFF1A2B4C;

	public final NumberSetting size = add(new NumberSetting("size", "Size", 110, 64, 200, 2, " px"));
	public final NumberSetting zoom = add(new NumberSetting("zoom", "Zoom", 1, 1, 4, 0.5, "x"));
	public final BooleanSetting rotate = add(new BooleanSetting("rotate", "Turn with me", false));
	public final BooleanSetting waypoints = add(new BooleanSetting("waypoints", "Waypoints", true));
	public final BooleanSetting death = add(new BooleanSetting("death", "Last death", true));
	public final BooleanSetting coordinates = add(new BooleanSetting("coordinates", "Coordinates", true));

	private final MinimapImage image = new MinimapImage();
	private Object texture;
	private int textureSize;
	private int builtX = Integer.MIN_VALUE, builtZ, sinceBuild;

	public MinimapModule() {
		super("minimap", "Minimap", "A small map with your waypoints.", Category.WORLD, false, 366, 4);
	}

	private int mapSize() {
		return size.getInt();
	}

	/** Blocks the picture covers: the map's width (or its diagonal when it turns), plus a margin. */
	private int blocksAcross() {
		double across = mapSize() / zoom.get();
		if (rotate.on()) across *= Math.sqrt(2);
		int n = (int) Math.ceil(across) + 4;
		return n + (n & 1);
	}

	@Override
	public int width(boolean preview) {
		return mapSize();
	}

	@Override
	public int height(boolean preview) {
		return mapSize() + (coordinates.on() ? 11 : 0);
	}

	@Override
	public boolean hasContent() {
		return p().inWorld() && p().hasPlayer();
	}

	@Override
	protected void onDisable() {
		release();
	}

	private void release() {
		if (texture != null) p().destroyTexture(texture);
		texture = null;
		textureSize = 0;
		image.clear();
		builtX = Integer.MIN_VALUE;
	}

	@Override
	public void tick() {
		if (!isEnabled() || !p().inWorld() || !p().hasPlayer()) return;
		int across = blocksAcross();
		int px = (int) Math.floor(p().playerX()), pz = (int) Math.floor(p().playerZ());
		int scanY = p().dimensionHasCeiling() ? (int) Math.floor(p().playerY()) + 1 : Integer.MIN_VALUE;
		String world = p().worldId() + "|" + p().dimension();
		boolean changed = image.update((cx, cz, y, rgb, h, w) -> p().mapChunk(cx, cz, y, rgb, h, w), world, px, pz, across / 2 + 8, scanY, System.currentTimeMillis());
		if (texture == null || textureSize != across) {
			if (texture != null) p().destroyTexture(texture);
			texture = p().createTexture("minimap", across, across);
			textureSize = across;
			changed = true;
		}
		if (texture == null) return;
		if (changed || px != builtX || pz != builtZ || ++sinceBuild >= 10) {
			image.build(across, px, pz);
			p().uploadTexture(texture, image.pixels());
			builtX = px;
			builtZ = pz;
			sinceBuild = 0;
		}
	}

	@Override
	protected void draw(Gfx g, boolean preview) {
		int s = mapSize();
		float half = s / 2f;
		g.fill(-1, -1, s + 1, s + 1, BORDER);
		g.fill(0, 0, s, s, BACKGROUND);
		boolean live = p().inWorld() && p().hasPlayer() && texture != null && image.size() > 0;
		if (!live) {
			Ui.icon(g, Icon.MAP, half, half - 5, Draw.MUTED, UiFont.ICON_M);
			String label = "Minimap";
			g.text(label, half - Draw.width(label) / 2f, half + 5, Draw.MUTED, true);
			if (coordinates.on()) centred(g, "0, 64, 0", s + 3);
			return;
		}
		double px = p().playerX(), pz = p().playerZ();
		float z = zoom.getFloat();
		float yaw = p().playerYaw();
		float turn = rotate.on() ? 180f - yaw : 0f;
		g.scissor(0, 0, s, s);
		g.push();
		g.translate(half, half);
		if (turn != 0) g.rotate(turn);
		int n = image.size();
		g.blitHandle(texture, (float) ((image.originX() - px) * z), (float) ((image.originZ() - pz) * z), n * z, n * z);
		g.pop();
		g.endScissor();

		double rad = Math.toRadians(turn), cos = Math.cos(rad), sin = Math.sin(rad);
		float edge = half - 4;
		if (waypoints.on()) {
			String worldId = p().worldId(), dim = p().dimension();
			if (worldId != null) {
				for (Waypoint w : WaypointStore.of(worldId)) {
					if (!w.visible || dim == null || !dim.equals(w.dim)) continue;
					marker(g, (w.x + 0.5 - px) * z, (w.z + 0.5 - pz) * z, cos, sin, half, edge, w.color, null);
				}
			}
		}
		if (death.on()) {
			DeathWaypointModule dm = (DeathWaypointModule) Polar.modules().byId("death_waypoint");
			double[] d = dm == null ? null : dm.lastDeath();
			if (d != null && p().dimension() != null && p().dimension().equals(dm.lastDeathDimension())) {
				marker(g, (Math.floor(d[0]) + 0.5 - px) * z, (Math.floor(d[2]) + 0.5 - pz) * z, cos, sin, half, edge, Draw.DANGER, Icon.SKULL);
			}
		}
		// north
		float nx = half + (float) (sin * edge), ny = half - (float) (cos * edge);
		g.fill(Math.round(nx) - 4, Math.round(ny) - 4, Math.round(nx) + 4, Math.round(ny) + 5, 0xCC0B1426);
		g.text("N", nx - Draw.width("N") / 2f + 0.5f, ny - 3.5f, Draw.DANGER, false);
		// you
		g.push();
		g.translate(half, half);
		if (!rotate.on()) g.rotate(yaw + 180f);
		g.scale(0.8f);
		Ui.icon(g, Icon.NAVIGATION, 0, 0, 0xFF000000, UiFont.ICON_M_FILL);
		g.scale(0.85f);
		Ui.icon(g, Icon.NAVIGATION, 0, 0, Draw.WHITE, UiFont.ICON_M_FILL);
		g.pop();
		if (coordinates.on()) {
			centred(g, String.format(Locale.ROOT, "%d, %d, %d", (int) Math.floor(px), (int) Math.floor(p().playerY()), (int) Math.floor(pz)), s + 3);
		}
	}

	/** A waypoint dot (or icon) at an offset from the centre in GUI px; kept on the edge when it's off the map. */
	private void marker(Gfx g, double dx, double dy, double cos, double sin, float half, float edge, int color, String icon) {
		double x = dx * cos - dy * sin, y = dx * sin + dy * cos;
		double far = Math.max(Math.abs(x), Math.abs(y));
		boolean outside = far > edge;
		if (outside) {
			x *= edge / far;
			y *= edge / far;
		}
		float cx = half + (float) x, cy = half + (float) y;
		if (icon != null) {
			Ui.icon(g, icon, cx, cy, outside ? Draw.alpha(color, 0.7f) : color, UiFont.ICON_S);
			return;
		}
		int r = outside ? 1 : 2;
		int ix = Math.round(cx), iy = Math.round(cy);
		g.fill(ix - r - 1, iy - r - 1, ix + r + 1, iy + r + 1, 0xFF000000);
		g.fill(ix - r, iy - r, ix + r, iy + r, 0xFF000000 | color);
	}

	private void centred(Gfx g, String text, int y) {
		g.text(text, (mapSize() - Draw.width(text)) / 2f, y, Draw.WHITE, true);
	}
}
