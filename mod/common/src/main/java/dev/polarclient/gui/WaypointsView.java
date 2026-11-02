package dev.polarclient.gui;

import dev.polarclient.Polar;
import dev.polarclient.gfx.Gfx;
import dev.polarclient.gui.ui.Fonts;
import dev.polarclient.gui.ui.Icon;
import dev.polarclient.gui.ui.TextField;
import dev.polarclient.gui.ui.Theme;
import dev.polarclient.gui.ui.Ui;
import dev.polarclient.modules.world.WaypointsModule;
import dev.polarclient.world.Waypoint;
import dev.polarclient.world.WaypointStore;

import java.util.List;

/** Lists the current world's waypoints: add one here, rename, recolour, move, hide or delete. */
public class WaypointsView extends View {
	private static final float DW = 400, DH = 256, R = 12, ROW = 28, LIST_Y = 72;
	/** Waypoint colours (new waypoints take the next one). */
	public static final int[] COLORS = {0xFF8CC8FF, 0xFF5CF2D6, 0xFF6EE7A0, 0xFFFFE066, 0xFFFFA94D, 0xFFFF6B6B, 0xFFB197FC};
	private static final float FIELD_W = 114, SWATCH_X = 140, SWATCH_STEP = 14, ADD_W = 78, SAVE_W = 54, MOVE_W = 76;

	private final TextField name = new TextField(32);
	private float zoom = 1, ox, oy;
	private float scroll, scrollTarget;
	private Waypoint selected;
	private Waypoint confirmDelete;
	private long confirmAt;
	private int color = COLORS[0];

	public WaypointsView(Object parent) {
		super(parent);
		name.setTextColor(0xFFFFFFFF);
	}

	@Override
	public String title() {
		return "Waypoints";
	}

	@Override
	public boolean pausesGame() {
		return false;
	}

	@Override
	protected void init() {
		int gs = Math.max(1, p().guiScale());
		float px = (float) Math.floor(Math.min(width * gs * 0.9f / DW, height * gs * 0.9f / DH));
		zoom = Math.max(1, px) / gs;
		ox = Math.round((width - DW * zoom) / 2f);
		oy = Math.round((height - DH * zoom) / 2f);
	}

	private List<Waypoint> list() {
		return WaypointStore.of(p().worldId());
	}

	private float mx(double x) {
		return (float) ((x - ox) / zoom);
	}

	private float my(double y) {
		return (float) ((y - oy) / zoom);
	}

	@Override
	public void renderBackground(Gfx g, int mouseX, int mouseY) {
		g.fill(0, 0, width, height, 0x88020814);
	}

	@Override
	public void render(Gfx g, int mouseX, int mouseY, float partialTicks) {
		Ui.frame();
		Theme t = Theme.current();
		int accent = t.primary(), highlight = Theme.mix(accent, 0xFF40E0FF, 0.45f);
		float mx = mx(mouseX), my = my(mouseY);
		scroll = Ui.approach(scroll, scrollTarget, 18f);
		g.push();
		g.translate(ox, oy);
		g.scale(zoom);
		Ui.setZoom(zoom);
		try {
			Ui.rect(g, -1.2f, -1.2f, DW + 2.4f, DH + 2.4f, R + 1.2f, Theme.alpha(highlight, 0.16f));
			Ui.rect(g, 0, 0, DW, DH, R, Theme.alpha(0xFF061328, 0.95f));
			Ui.ring(g, 0, 0, DW, DH, R, Theme.alpha(Theme.mix(0xFF1E5B95, highlight, 0.35f), 0.95f), 1.5f);

			// header
			Ui.icon(g, Icon.PIN_DROP, 22, 22, highlight, Fonts.ICON_M);
			Ui.textMid(g, "Waypoints", 36, 22, 0xFFF1F6FC, Fonts.BOLD);
			String world = p().worldId();
			String where = world == null ? "Join a world to add waypoints" : world.replaceFirst("^server:", "").replaceFirst("^world:", "") + "  ·  " + list().size() + " saved";
			Ui.textMid(g, where, 36 + Fonts.width("Waypoints", Fonts.BOLD) + 10, 22.5f, 0xFF8FA3BE, Fonts.SMALL);
			boolean closeHover = Ui.inside(mx, my, DW - 34, 10, 24, 24);
			Ui.rect(g, DW - 34, 10, 24, 24, 12, closeHover ? 0x332A4A72 : 0);
			Ui.icon(g, Icon.CLOSE, DW - 22, 22, 0xFFD2DDEA, Fonts.ICON_S);

			// editor row: name, colours, buttons
			float ey = 40;
			Ui.outlined(g, 16, ey, FIELD_W, 20, 10, 0xFF061226, name.focused() ? accent : 0xFF2A4A72, name.focused() ? 2 : 1);
			name.setBounds(24, Math.round(ey + 6), Math.round(FIELD_W) - 16);
			name.visible = world != null;
			if (name.value().isEmpty() && !name.focused()) g.text(selected == null ? "Name (optional)" : "Name", 24, Math.round(ey + 6), 0xFF6F819A, false);
			name.render(g);
			float sx = SWATCH_X;
			for (int c : COLORS) {
				boolean picked = (color & 0xFFFFFF) == (c & 0xFFFFFF);
				if (picked) Ui.circle(g, sx + 6, ey + 10, 7.5f, 0xFFF1F6FC);
				Ui.circle(g, sx + 6, ey + 10, 6, c);
				sx += SWATCH_STEP;
			}
			if (selected == null) {
				button(g, "Add here", Icon.ADD, DW - 16 - ADD_W, ey, ADD_W, mx, my, accent, true);
			} else {
				button(g, "Save", Icon.CHECK, DW - 16 - SAVE_W, ey, SAVE_W, mx, my, accent, true);
				button(g, "Move here", Icon.LOCATION, DW - 16 - SAVE_W - 6 - MOVE_W, ey, MOVE_W, mx, my, accent, false);
			}

			// list
			Ui.hline(g, 16, LIST_Y - 6, DW - 32, 1, 0xFF143259);
			float top = LIST_Y, bottom = DH - 12;
			g.scissor(0, Math.round(top), Math.round(DW), Math.round(bottom));
			List<Waypoint> list = list();
			if (list.isEmpty()) {
				Ui.textCentered(g, world == null ? "No world loaded." : "No waypoints yet. Press Add here (or B in game).", DW / 2f, top + 30, 0xFF8FA3BE, Fonts.BODY);
			}
			float y = top - scroll;
			String dim = p().dimension();
			for (Waypoint w : list) {
				if (y + ROW >= top && y <= bottom) drawRow(g, w, y, mx, my, dim, accent);
				y += ROW + 4;
			}
			g.endScissor();
		} finally {
			Ui.setZoom(1f);
			g.pop();
		}
	}

	private void button(Gfx g, String label, String icon, float x, float y, float w, float mx, float my, int accent, boolean primary) {
		boolean hover = Ui.inside(mx, my, x, y, w, 20);
		int chip = Theme.mix(accent, 0xFF4FB0FF, 0.45f);
		if (primary) Ui.gradientRect(g, x, y, w, 20, 10, Theme.mix(chip, 0xFFFFFFFF, hover ? 0.22f : 0.12f), Theme.mix(chip, 0xFF2A7BD8, 0.25f));
		else Ui.outlined(g, x, y, w, 20, 10, hover ? 0xFF16305C : 0xFF0B1A33, 0xFF2A4A72, 1);
		float tw = Fonts.width(label, Fonts.BODY) + 14;
		float cx = x + (w - tw) / 2f;
		Ui.icon(g, icon, cx + 5, y + 10, 0xFFFFFFFF, Fonts.ICON_S);
		Ui.textMid(g, label, cx + 14, y + 10, 0xFFFFFFFF, Fonts.BODY);
	}

	private void drawRow(Gfx g, Waypoint w, float y, float mx, float my, String dim, int accent) {
		float x = 16, rw = DW - 32;
		boolean hover = Ui.inside(mx, my, x, y, rw, ROW), sel = w == selected;
		Ui.rect(g, x, y, rw, ROW, 7, sel ? Theme.mix(0xFF0B1A33, accent, 0.25f) : hover ? 0xFF0E2140 : 0xFF0A1830);
		if (sel) Ui.ring(g, x, y, rw, ROW, 7, accent, 1);
		boolean here = dim != null && dim.equals(w.dim);
		int text = w.visible ? 0xFFF1F6FC : 0xFF6F819A;
		Ui.circle(g, x + 14, y + ROW / 2f, 5, w.visible ? w.color : Theme.alpha(w.color, 0.4f));
		Ui.text(g, Fonts.ellipsize(w.name, Fonts.BOLD, 150), x + 26, y + 5, text, Fonts.BOLD);
		String info = w.x + ", " + w.y + ", " + w.z + "  ·  " + dimName(w.dim)
			+ (here && p().hasPlayer() ? "  ·  " + WaypointsModule.formatDistance(w.distanceTo(p().playerX(), p().playerY(), p().playerZ())) : "");
		Ui.text(g, info, x + 26, y + 16, 0xFF8FA3BE, Fonts.SMALL);
		// visibility and delete
		float bx = x + rw - 26;
		boolean delHover = Ui.inside(mx, my, bx, y + 4, 20, 20);
		boolean armed = w == confirmDelete && System.currentTimeMillis() - confirmAt < 2500;
		Ui.rect(g, bx, y + 4, 20, 20, 10, armed ? 0xFFB3261E : delHover ? 0x33FF6B6B : 0);
		Ui.icon(g, Icon.DELETE, bx + 10, y + 14, armed ? 0xFFFFFFFF : 0xFFFF8A8A, Fonts.ICON_S);
		if (armed) Ui.textRight(g, "Click again to delete", bx - 30, y + ROW / 2f, 0xFFFF8A8A, Fonts.SMALL);
		float vx = bx - 24;
		boolean visHover = Ui.inside(mx, my, vx, y + 4, 20, 20);
		Ui.rect(g, vx, y + 4, 20, 20, 10, visHover ? 0x332A4A72 : 0);
		Ui.icon(g, w.visible ? Icon.EYE : Icon.VISIBILITY_OFF, vx + 10, y + 14, 0xFFD2DDEA, Fonts.ICON_S);
	}

	private static String dimName(String id) {
		if (id == null) return "?";
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		if (path.equals("overworld") || path.equals("0")) return "Overworld";
		if (path.equals("the_nether") || path.equals("-1")) return "Nether";
		if (path.equals("the_end") || path.equals("1")) return "End";
		return path;
	}

	private float maxScroll() {
		return Math.max(0, list().size() * (ROW + 4) - (DH - 12 - LIST_Y));
	}

	private void select(Waypoint w) {
		selected = w;
		confirmDelete = null;
		if (w != null) {
			name.setValue(w.name);
			color = w.color;
		} else {
			name.setValue("");
		}
	}

	private void addOrSave() {
		String world = p().worldId();
		if (world == null) return;
		String n = name.value().trim();
		if (selected != null) {
			if (!n.isEmpty()) selected.name = n;
			selected.color = color;
			WaypointStore.save();
			select(null);
			return;
		}
		if (!p().hasPlayer()) return;
		WaypointsModule module = Polar.modules().waypoints;
		int before = list().size();
		module.addHere();
		if (list().size() > before) {
			Waypoint w = list().get(list().size() - 1);
			if (!n.isEmpty()) w.name = n;
			w.color = color;
			WaypointStore.save();
			name.setValue("");
			scrollTarget = maxScroll();
		}
	}

	@Override
	public boolean mouseClicked(double rawX, double rawY, int button) {
		float mx = mx(rawX), my = my(rawY);
		if (name.mouseClicked(mx, my)) return true;
		if (Ui.inside(mx, my, DW - 34, 10, 24, 24)) {
			onClose();
			return true;
		}
		float ey = 40;
		float sx = SWATCH_X;
		for (int c : COLORS) {
			if (Ui.inside(mx, my, sx - 1, ey + 3, 14, 14)) {
				color = c;
				p().playClickSound();
				return true;
			}
			sx += SWATCH_STEP;
		}
		if (selected == null && Ui.inside(mx, my, DW - 16 - ADD_W, ey, ADD_W, 20)) {
			p().playClickSound();
			addOrSave();
			return true;
		}
		if (selected != null && Ui.inside(mx, my, DW - 16 - SAVE_W, ey, SAVE_W, 20)) {
			p().playClickSound();
			addOrSave();
			return true;
		}
		if (selected != null && Ui.inside(mx, my, DW - 16 - SAVE_W - 6 - MOVE_W, ey, MOVE_W, 20) && p().hasPlayer()) {
			p().playClickSound();
			selected.x = (int) Math.floor(p().playerX());
			selected.y = (int) Math.floor(p().playerY());
			selected.z = (int) Math.floor(p().playerZ());
			selected.dim = p().dimension();
			WaypointStore.save();
			return true;
		}
		if (my >= LIST_Y && my <= DH - 12) {
			float y = LIST_Y - scroll;
			for (Waypoint w : list()) {
				if (Ui.inside(mx, my, 16, y, DW - 32, ROW)) {
					float bx = 16 + DW - 32 - 26;
					if (Ui.inside(mx, my, bx, y + 4, 20, 20)) {
						p().playClickSound();
						if (w == confirmDelete && System.currentTimeMillis() - confirmAt < 2500) {
							list().remove(w);
							WaypointStore.save();
							if (selected == w) select(null);
							confirmDelete = null;
							scrollTarget = Math.min(scrollTarget, maxScroll());
						} else {
							confirmDelete = w;
							confirmAt = System.currentTimeMillis();
						}
						return true;
					}
					if (Ui.inside(mx, my, bx - 24, y + 4, 20, 20)) {
						p().playClickSound();
						w.visible = !w.visible;
						WaypointStore.save();
						return true;
					}
					select(w == selected ? null : w);
					return true;
				}
				y += ROW + 4;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double amount) {
		scrollTarget = (float) Math.max(0, Math.min(maxScroll(), scrollTarget - amount * 24));
		return true;
	}

	@Override
	public boolean keyPressed(KeyInput key) {
		if (name.focused()) {
			if (key.confirm()) {
				addOrSave();
				name.setFocused(false);
				return true;
			}
			if (key.is(KeyInput.Key.ESCAPE)) {
				name.setFocused(false);
				return true;
			}
			return name.keyPressed(key);
		}
		if (key.is(KeyInput.Key.ESCAPE) && selected != null) {
			select(null);
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(char c) {
		return name.charTyped(c);
	}
}
