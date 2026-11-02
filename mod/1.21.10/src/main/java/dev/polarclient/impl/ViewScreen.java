package dev.polarclient.impl;

import com.mojang.blaze3d.platform.InputConstants;
import dev.polarclient.gui.KeyInput;
import dev.polarclient.gui.View;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Shows a Polar {@link View} as a 1.21.10 screen, forwarding drawing and input. */
public final class ViewScreen extends Screen {
	public final View view;

	public ViewScreen(View view) {
		super(Component.literal(view.title()));
		this.view = view;
	}

	@Override
	protected void init() {
		view.init(width, height);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
		view.renderBackground(McGfx.of(g), mouseX, mouseY);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
		view.render(McGfx.of(g), mouseX, mouseY, a);
	}

	/** GLFW buttons (0 left, 1 right, 2 middle) to Polar's (the same numbers). */
	private static int button(int glfw) {
		switch (glfw) {
			case InputConstants.MOUSE_BUTTON_LEFT: return View.LEFT;
			case InputConstants.MOUSE_BUTTON_RIGHT: return View.RIGHT;
			case InputConstants.MOUSE_BUTTON_MIDDLE: return 2;
			default: return 10 + glfw;
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return view.mouseClicked(event.x(), event.y(), button(event.button()));
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return view.mouseReleased(event.x(), event.y(), button(event.button()));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return view.mouseDragged(event.x(), event.y(), button(event.button()), dx, dy);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		return view.mouseScrolled(x, y, scrollY);
	}

	public static KeyInput key(KeyEvent e) {
		KeyInput.Key k;
		int code = e.key();
		if (e.isEscape()) k = KeyInput.Key.ESCAPE;
		else if (e.isConfirmation()) k = KeyInput.Key.ENTER;
		else if (code == InputConstants.KEY_BACKSPACE) k = KeyInput.Key.BACKSPACE;
		else if (code == InputConstants.KEY_DELETE) k = KeyInput.Key.DELETE;
		else if (e.isLeft()) k = KeyInput.Key.LEFT;
		else if (e.isRight()) k = KeyInput.Key.RIGHT;
		else if (e.isUp()) k = KeyInput.Key.UP;
		else if (e.isDown()) k = KeyInput.Key.DOWN;
		else if (code == InputConstants.KEY_HOME) k = KeyInput.Key.HOME;
		else if (code == InputConstants.KEY_END) k = KeyInput.Key.END;
		else if (code == InputConstants.KEY_TAB) k = KeyInput.Key.TAB;
		else if (code == InputConstants.KEY_F5) k = KeyInput.Key.F5;
		else if (code == InputConstants.KEY_A) k = KeyInput.Key.A;
		else if (code == InputConstants.KEY_C) k = KeyInput.Key.C;
		else if (code == InputConstants.KEY_V) k = KeyInput.Key.V;
		else if (code == InputConstants.KEY_X) k = KeyInput.Key.X;
		else k = KeyInput.Key.OTHER;
		return new KeyInput(k, e.hasShiftDown(), e.hasControlDown(), Keys.get(dev.polarclient.platform.PolarKey.MENU).matches(e));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (view.keyPressed(key(event))) return true;
		if (event.isEscape() && view.closeOnEscape()) {
			view.onClose();
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!event.isAllowedChatCharacter()) return false;
		boolean used = false;
		for (char c : Character.toChars(event.codepoint())) used |= view.charTyped(c);
		return used;
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
