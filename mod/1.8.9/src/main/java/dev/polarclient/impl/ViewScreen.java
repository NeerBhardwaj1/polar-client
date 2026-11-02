package dev.polarclient.impl;

import dev.polarclient.gui.KeyInput;
import dev.polarclient.gui.View;
import dev.polarclient.platform.PolarKey;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.function.Consumer;

/**
 * Shows a Polar {@link View} as a 1.8.9 screen, forwarding drawing and input. Vanilla dialogs opened from it (Add
 * Server, Delete, ...) report back through {@link #confirmResult}, which hands the answer to {@link #onConfirm}.
 */
public final class ViewScreen extends Screen {
	public final View view;
	/** Receives the answer of the dialog opened from this screen. */
	Consumer<Boolean> onConfirm;
	private int lastX, lastY;

	public ViewScreen(View view) {
		this.view = view;
	}

	@Override
	public void init() {
		view.init(width, height);
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTicks) {
		McGfx g = McGfx.begin();
		view.renderBackground(g, mouseX, mouseY);
		g.nextLayer();
		view.render(g, mouseX, mouseY, partialTicks);
		g.end();
	}

	/** LWJGL buttons (0 left, 1 right, 2 middle) are Polar's numbers already. */
	@Override
	protected void mouseClicked(int x, int y, int button) {
		lastX = x;
		lastY = y;
		view.mouseClicked(x, y, button);
	}

	@Override
	protected void mouseReleased(int x, int y, int button) {
		view.mouseReleased(x, y, button);
	}

	@Override
	protected void mouseDragged(int x, int y, int button, long heldMs) {
		view.mouseDragged(x, y, button, x - lastX, y - lastY);
		lastX = x;
		lastY = y;
	}

	@Override
	public void handleMouse() {
		super.handleMouse();
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0) {
			int x = Mouse.getEventX() * width / client.width;
			int y = height - Mouse.getEventY() * height / client.height - 1;
			view.mouseScrolled(x, y, wheel > 0 ? 1 : -1);
		}
	}

	public static KeyInput key(int code) {
		KeyInput.Key k;
		switch (code) {
			case Keyboard.KEY_ESCAPE: k = KeyInput.Key.ESCAPE; break;
			case Keyboard.KEY_RETURN:
			case Keyboard.KEY_NUMPADENTER: k = KeyInput.Key.ENTER; break;
			case Keyboard.KEY_BACK: k = KeyInput.Key.BACKSPACE; break;
			case Keyboard.KEY_DELETE: k = KeyInput.Key.DELETE; break;
			case Keyboard.KEY_LEFT: k = KeyInput.Key.LEFT; break;
			case Keyboard.KEY_RIGHT: k = KeyInput.Key.RIGHT; break;
			case Keyboard.KEY_UP: k = KeyInput.Key.UP; break;
			case Keyboard.KEY_DOWN: k = KeyInput.Key.DOWN; break;
			case Keyboard.KEY_HOME: k = KeyInput.Key.HOME; break;
			case Keyboard.KEY_END: k = KeyInput.Key.END; break;
			case Keyboard.KEY_TAB: k = KeyInput.Key.TAB; break;
			case Keyboard.KEY_F5: k = KeyInput.Key.F5; break;
			case Keyboard.KEY_A: k = KeyInput.Key.A; break;
			case Keyboard.KEY_C: k = KeyInput.Key.C; break;
			case Keyboard.KEY_V: k = KeyInput.Key.V; break;
			case Keyboard.KEY_X: k = KeyInput.Key.X; break;
			default: k = KeyInput.Key.OTHER;
		}
		return new KeyInput(k, hasShiftDown(), hasControlDown(), code != 0 && code == Keys.get(PolarKey.MENU).getCode());
	}

	@Override
	protected void keyPressed(char c, int code) {
		// 1.8.9 sends the key and the character it types together: give text boxes the character first
		KeyInput k = key(code);
		if (c >= ' ' && c != 127 && c != '\u00a7' && !k.control && view.charTyped(c)) return;
		if (view.keyPressed(k)) return;
		if (code == Keyboard.KEY_ESCAPE && view.closeOnEscape()) view.onClose();
	}

	@Override
	public void tick() {
		view.tick();
	}

	@Override
	public void removed() {
		view.removed();
	}

	@Override
	public boolean shouldPauseGame() {
		return view.pausesGame();
	}

	@Override
	public void confirmResult(boolean ok, int id) {
		Consumer<Boolean> callback = onConfirm;
		onConfirm = null;
		if (callback != null) callback.accept(ok);
	}
}
