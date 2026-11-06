package dev.polarclient.module;

import com.google.gson.JsonObject;
import dev.polarclient.gfx.Gfx;
import dev.polarclient.setting.NumberSetting;

/**
 * A module drawn on the HUD that can be dragged and scaled in the HUD editor.
 * Positions are stored relative to the nearest screen edge (or centre), so elements stay in place
 * when the window is resized and grow away from the edge they're pinned to.
 */
public abstract class HudModule extends Module {
	/** Reference screen size used for the default layout (GUI-scaled 1080p at scale 4). */
	public static final double REF_W = 480, REF_H = 270;

	private enum Anchor { START, CENTER, END }

	public final NumberSetting scale;

	private final double defaultX, defaultY;
	private double fx, fy;
	private Anchor anchorX = Anchor.START, anchorY = Anchor.START;
	private int lastX, lastY, lastW, lastH;
	/** Set until the first render, when the default spot is converted into an edge-anchored position. */
	private boolean needsDefaultPlacement;

	protected HudModule(String id, String name, String description, boolean enabledByDefault, double refX, double refY) {
		this(id, name, description, Category.HUD, enabledByDefault, refX, refY);
	}

	protected HudModule(String id, String name, String description, Category category, boolean enabledByDefault, double refX, double refY) {
		super(id, name, description, category, enabledByDefault);
		this.scale = add(new NumberSetting("scale", "Scale", 1.0, 0.5, 2.5, 0.05, "x"));
		this.defaultX = refX;
		this.defaultY = refY;
		resetPosition();
	}

	/** Unscaled size of the element. {@code preview} is true in the HUD editor (show sample data). */
	public abstract int width(boolean preview);

	public abstract int height(boolean preview);

	/** Draws the element with its top-left corner at (0, 0), unscaled. */
	protected abstract void draw(Gfx g, boolean preview);

	/** Whether there is anything to show outside the editor (e.g. no potion effects = hidden). */
	public boolean hasContent() {
		return true;
	}

	/** Called as {@link #render} starts and ends, so a module can work out what it shows once per frame. */
	protected void beginRender(boolean preview) {
	}

	protected void endRender() {
	}

	public final void render(Gfx g, int screenW, int screenH, boolean preview) {
		if (!preview && !hasContent()) return;
		beginRender(preview);
		try {
			place(g, screenW, screenH, preview);
		} finally {
			endRender();
		}
	}

	private void place(Gfx g, int screenW, int screenH, boolean preview) {
		float s = scale.getFloat();
		int w = Math.max(1, (int) Math.ceil(width(preview) * s));
		int h = Math.max(1, (int) Math.ceil(height(preview) * s));
		if (needsDefaultPlacement) {
			needsDefaultPlacement = false;
			lastW = w;
			lastH = h;
			setPosition((int) Math.round(defaultX / REF_W * screenW), (int) Math.round(defaultY / REF_H * screenH), screenW, screenH);
		}
		int x = resolve(fx, anchorX, w, screenW);
		int y = resolve(fy, anchorY, h, screenH);
		lastX = x;
		lastY = y;
		lastW = w;
		lastH = h;
		g.push();
		g.translate(x, y);
		g.scale(s);
		draw(g, preview);
		g.pop();
	}

	private static int resolve(double f, Anchor anchor, int size, int screen) {
		double edge = f * screen;
		double start;
		switch (anchor) {
			case CENTER: start = edge - size / 2.0; break;
			case END: start = edge - size; break;
			default: start = edge; break;
		}
		return (int) Math.round(Math.max(0, Math.min(screen - size, start)));
	}

	/** Moves the element so its top-left is at (x, y), re-picking the anchor from where it now sits. */
	public void setPosition(int x, int y, int screenW, int screenH) {
		int w = Math.max(1, lastW), h = Math.max(1, lastH);
		x = Math.max(0, Math.min(screenW - w, x));
		y = Math.max(0, Math.min(screenH - h, y));
		double cx = x + w / 2.0, cy = y + h / 2.0;
		anchorX = cx < screenW / 3.0 ? Anchor.START : cx > screenW * 2 / 3.0 ? Anchor.END : Anchor.CENTER;
		anchorY = cy < screenH / 3.0 ? Anchor.START : cy > screenH * 2 / 3.0 ? Anchor.END : Anchor.CENTER;
		switch (anchorX) {
			case CENTER: fx = cx / screenW; break;
			case END: fx = (x + w) / (double) screenW; break;
			default: fx = x / (double) screenW; break;
		}
		switch (anchorY) {
			case CENTER: fy = cy / screenH; break;
			case END: fy = (y + h) / (double) screenH; break;
			default: fy = y / (double) screenH; break;
		}
		lastX = x;
		lastY = y;
	}

	public void resetPosition() {
		anchorX = Anchor.START;
		anchorY = Anchor.START;
		fx = defaultX / REF_W;
		fy = defaultY / REF_H;
		needsDefaultPlacement = true;
	}

	public int lastX() {
		return lastX;
	}

	public int lastY() {
		return lastY;
	}

	public int lastWidth() {
		return lastW;
	}

	public int lastHeight() {
		return lastH;
	}

	public boolean contains(double mx, double my) {
		return mx >= lastX && mx < lastX + lastW && my >= lastY && my < lastY + lastH;
	}

	@Override
	public JsonObject save() {
		JsonObject o = super.save();
		JsonObject pos = new JsonObject();
		pos.addProperty("x", fx);
		pos.addProperty("y", fy);
		pos.addProperty("anchorX", anchorX.name());
		pos.addProperty("anchorY", anchorY.name());
		o.add("position", pos);
		return o;
	}

	@Override
	public void load(JsonObject o) {
		super.load(o);
		if (o.has("position") && o.get("position").isJsonObject()) {
			JsonObject pos = o.getAsJsonObject("position");
			try {
				fx = pos.get("x").getAsDouble();
				fy = pos.get("y").getAsDouble();
				anchorX = Anchor.valueOf(pos.get("anchorX").getAsString());
				anchorY = Anchor.valueOf(pos.get("anchorY").getAsString());
				needsDefaultPlacement = false;
			} catch (RuntimeException e) {
				resetPosition();
			}
		}
	}
}
