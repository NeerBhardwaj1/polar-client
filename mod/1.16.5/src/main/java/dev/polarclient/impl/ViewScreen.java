package dev.polarclient.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.polarclient.gui.KeyInput;
import dev.polarclient.gui.View;
import dev.polarclient.platform.PolarKey;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import org.lwjgl.glfw.GLFW;

/** Shows a Polar {@link View} as a 1.16.5 screen, forwarding drawing and input. */
public final class ViewScreen extends Screen {
	public final View view;

	public ViewScreen(View view) {
		super(new TextComponent(view.title()));
		this.view = view;
	}

	@Override
	protected void init() {
		view.init(width, height);
	}

	@Override
	public void render(PoseStack pose, int mouseX, int mouseY, float partialTicks) {
		McGfx g = McGfx.of(pose);
		view.renderBackground(g, mouseX, mouseY);
		g.nextLayer();
		view.render(g, mouseX, mouseY, partialTicks);
		g.end();
	}

	/** GLFW buttons (0 left, 1 right, 2 middle) are Polar's numbers already. */
	@Override
	public boolean mouseClicked(double x, double y, int button) {
		return view.mouseClicked(x, y, button);
	}

	@Override
	public boolean mouseReleased(double x, double y, int button) {
		return view.mouseReleased(x, y, button);
	}

	@Override
	public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
		return view.mouseDragged(x, y, button, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double amount) {
		return view.mouseScrolled(x, y, amount);
	}

	public static KeyInput key(int code, int scanCode) {
		KeyInput.Key k;
		switch (code) {
			case GLFW.GLFW_KEY_ESCAPE: k = KeyInput.Key.ESCAPE; break;
			case GLFW.GLFW_KEY_ENTER:
			case GLFW.GLFW_KEY_KP_ENTER: k = KeyInput.Key.ENTER; break;
			case GLFW.GLFW_KEY_BACKSPACE: k = KeyInput.Key.BACKSPACE; break;
			case GLFW.GLFW_KEY_DELETE: k = KeyInput.Key.DELETE; break;
			case GLFW.GLFW_KEY_LEFT: k = KeyInput.Key.LEFT; break;
			case GLFW.GLFW_KEY_RIGHT: k = KeyInput.Key.RIGHT; break;
			case GLFW.GLFW_KEY_UP: k = KeyInput.Key.UP; break;
			case GLFW.GLFW_KEY_DOWN: k = KeyInput.Key.DOWN; break;
			case GLFW.GLFW_KEY_HOME: k = KeyInput.Key.HOME; break;
			case GLFW.GLFW_KEY_END: k = KeyInput.Key.END; break;
			case GLFW.GLFW_KEY_TAB: k = KeyInput.Key.TAB; break;
			case GLFW.GLFW_KEY_F5: k = KeyInput.Key.F5; break;
			case GLFW.GLFW_KEY_A: k = KeyInput.Key.A; break;
			case GLFW.GLFW_KEY_C: k = KeyInput.Key.C; break;
			case GLFW.GLFW_KEY_V: k = KeyInput.Key.V; break;
			case GLFW.GLFW_KEY_X: k = KeyInput.Key.X; break;
			default: k = KeyInput.Key.OTHER;
		}
		return new KeyInput(k, hasShiftDown(), hasControlDown(), Keys.get(PolarKey.MENU).matches(code, scanCode));
	}

	@Override
	public boolean keyPressed(int code, int scanCode, int modifiers) {
		if (view.keyPressed(key(code, scanCode))) return true;
		if (code == GLFW.GLFW_KEY_ESCAPE && view.closeOnEscape()) {
			view.onClose();
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(char c, int modifiers) {
		if (c < ' ' || c == 127 || c == '§') return false;
		return view.charTyped(c);
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
	public boolean isPauseScreen() {
		return view.pausesGame();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return view.closeOnEscape();
	}

	@Override
	public void onClose() {
		view.onClose();
	}
}
