package dev.polarclient.gui;

import dev.polarclient.gfx.Gfx;
import dev.polarclient.platform.Platform;

/**
 * A Polar screen, independent of the Minecraft version. Each version wraps it in its own Screen subclass that forwards
 * rendering and input. Mouse buttons: 0 = left, 1 = right, 2 = middle.
 */
public abstract class View {
	public static final int LEFT = 0, RIGHT = 1;

	/** Screen to return to (an opaque handle from {@link Platform#currentScreen()}), may be null. */
	protected final Object parent;
	protected int width, height;

	protected View(Object parent) {
		this.parent = parent;
	}

	protected static Platform p() {
		return Platform.get();
	}

	/** Called when shown and whenever the window is resized. */
	public final void init(int width, int height) {
		this.width = width;
		this.height = height;
		init();
	}

	protected void init() {
	}

	public String title() {
		return "Polar Client";
	}

	/** Background, drawn before {@link #render}. */
	public void renderBackground(Gfx g, int mouseX, int mouseY) {
	}

	public abstract void render(Gfx g, int mouseX, int mouseY, float partialTicks);

	public boolean mouseClicked(double mx, double my, int button) {
		return false;
	}

	public boolean mouseReleased(double mx, double my, int button) {
		return false;
	}

	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		return false;
	}

	public boolean mouseScrolled(double mx, double my, double amount) {
		return false;
	}

	public boolean keyPressed(KeyInput key) {
		return false;
	}

	public boolean charTyped(char c) {
		return false;
	}

	public void tick() {
	}

	/** The view was closed or replaced. */
	public void removed() {
	}

	public boolean pausesGame() {
		return true;
	}

	/** Whether Escape closes the view (after {@link #keyPressed} didn't use it). */
	public boolean closeOnEscape() {
		return true;
	}

	/** Escape / back: returns to the parent screen. */
	public void onClose() {
		p().show(parent);
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}
}
