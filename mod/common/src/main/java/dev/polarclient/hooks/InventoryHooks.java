package dev.polarclient.hooks;

import dev.polarclient.Polar;
import dev.polarclient.gfx.Gfx;
import dev.polarclient.gui.KeyInput;
import dev.polarclient.gui.ui.TextField;
import dev.polarclient.module.ModuleManager;
import dev.polarclient.platform.ItemView;
import dev.polarclient.platform.Platform;
import dev.polarclient.util.Draw;

import java.util.List;
import java.util.Locale;

/**
 * Inventory search and the shulker box preview. Each version calls these from its container-screen hooks; positions
 * are GUI units on the screen.
 */
public final class InventoryHooks {
	private static final int FIELD_W = 84;
	private static final TextField FIELD = new TextField(40);
	private static Object screen;
	private static String query = "";

	static {
		FIELD.setTextColor(0xFFFFFFFF);
		FIELD.setListener(v -> query = v.trim().toLowerCase(Locale.ROOT));
	}

	private InventoryHooks() {
	}

	private static boolean searchOn() {
		ModuleManager m = Polar.modules();
		return m != null && m.inventorySearch.isEnabled();
	}

	private static boolean previewOn() {
		ModuleManager m = Polar.modules();
		return m != null && m.shulkerPreview.isEnabled() && m.shulkerPreview.unavailableReason() == null;
	}

	/**
	 * Draws the search box, dims slots that don't match and shows the shulker preview. Call after the slots and before
	 * (or instead of nothing after) the tooltip. {@code hovered} is the item under the mouse when nothing is carried.
	 */
	public static void render(Gfx g, Object current, int left, int top, int imageW, int imageH, int[] xs, int[] ys, ItemView[] items,
			ItemView hovered, int mouseX, int mouseY) {
		boolean search = searchOn(), preview = previewOn() && hovered != null;
		if (!search && !preview) return;
		g.nextLayer();
		if (search) {
			if (current != screen) {
				screen = current;
				FIELD.setFocused(false);
				if (!Polar.modules().inventorySearch.keep.on()) {
					FIELD.setValue("");
					query = "";
				}
			}
			if (!query.isEmpty()) {
				for (int i = 0; i < items.length; i++) {
					if (!matches(items[i])) g.fill(xs[i], ys[i], xs[i] + 16, ys[i] + 16, 0xC8101622);
				}
			}
			int fx = left + imageW - FIELD_W, fy = top - 15;
			if (fy < 2) fy = top + imageH + 3;
			Draw.round(g, fx, fy, FIELD_W, 13, 4, FIELD.focused() ? 0xF0123056 : 0xE00B1426);
			Draw.round(g, fx, fy + 12, FIELD_W, 1, 0, FIELD.focused() ? 0xFF8CC8FF : 0xFF3A5A86);
			FIELD.setBounds(fx + 4, fy + 3, FIELD_W - 8);
			FIELD.visible = true;
			if (FIELD.value().isEmpty() && !FIELD.focused()) g.text("Search...", fx + 4, fy + 3, 0xFF7C8DA6, false);
			FIELD.render(g);
		}
		if (preview) {
			List<ItemView> inside = Platform.get().containerContents(hovered);
			if (inside != null) drawPreview(g, inside, mouseX, mouseY);
		}
	}

	private static boolean matches(ItemView item) {
		if (item == null) return false;
		Platform p = Platform.get();
		String name = p.itemName(item);
		if (name != null && name.toLowerCase(Locale.ROOT).contains(query)) return true;
		if (Polar.modules().inventorySearch.insideShulkers.on()) {
			List<ItemView> inside = p.containerContents(item);
			if (inside != null) {
				for (ItemView in : inside) {
					String n = in == null ? null : p.itemName(in);
					if (n != null && n.toLowerCase(Locale.ROOT).contains(query)) return true;
				}
			}
		}
		return false;
	}

	private static void drawPreview(Gfx g, List<ItemView> inside, int mouseX, int mouseY) {
		int cols = 9, rows = Math.max(3, (inside.size() + cols - 1) / cols);
		int w = cols * 18 + 8, h = rows * 18 + 8;
		int x = Math.max(2, Math.min(g.width() - w - 2, mouseX + 8));
		int y = mouseY - h - 16;
		if (y < 2) {
			// no room above the mouse: to its left, level with it
			x = mouseX - w - 14;
			y = Math.max(2, Math.min(g.height() - h - 2, mouseY - h / 2));
			if (x < 2) x = Math.min(g.width() - w - 2, mouseX + 14);
		}
		Draw.round(g, x - 1, y - 1, w + 2, h + 2, 5, 0xFF2A4A72);
		Draw.round(g, x, y, w, h, 4, 0xF20B1426);
		for (int i = 0; i < cols * rows; i++) {
			int sx = x + 4 + (i % cols) * 18, sy = y + 4 + (i / cols) * 18;
			g.fill(sx, sy, sx + 18, sy + 18, (i + i / cols) % 2 == 0 ? 0x22FFFFFF : 0x14FFFFFF);
			ItemView item = i < inside.size() ? inside.get(i) : null;
			if (item != null) {
				g.item(item, sx + 1, sy + 1);
				g.itemDecorations(item, sx + 1, sy + 1);
			}
		}
	}

	public static boolean typing() {
		return searchOn() && FIELD.focused();
	}

	/** True when the click was on the search box (focus it). Clicks elsewhere unfocus it and go on to the game. */
	public static boolean mouseClicked(double mx, double my) {
		if (!searchOn() || !FIELD.visible) return false;
		return FIELD.mouseClicked(mx, my);
	}

	/** While typing in the search box every key belongs to it (E, number keys, Q). Escape and Enter stop typing. */
	public static boolean keyPressed(KeyInput k) {
		if (!typing()) return false;
		if (k.is(KeyInput.Key.ESCAPE) || k.confirm()) {
			FIELD.setFocused(false);
			return true;
		}
		FIELD.keyPressed(k);
		return true;
	}

	public static boolean charTyped(char c) {
		return typing() && FIELD.charTyped(c);
	}

	/** The container screen closed. */
	public static void closed() {
		FIELD.setFocused(false);
		FIELD.visible = false;
	}
}
