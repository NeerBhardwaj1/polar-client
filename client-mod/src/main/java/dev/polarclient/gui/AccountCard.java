package dev.polarclient.gui;

import dev.polarclient.account.AccountSwitcher;
import dev.polarclient.account.LauncherBridge;
import dev.polarclient.gui.ui.Fonts;
import dev.polarclient.gui.ui.Icon;
import dev.polarclient.gui.ui.Theme;
import dev.polarclient.gui.ui.Ui;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The account card in the top-right corner of Polar's menus. Its dropdown lists the launcher's accounts (switch with
 * one click, no restart) plus a few shortcuts supplied by the screen.
 */
public final class AccountCard {
	public record Shortcut(String icon, String label, Runnable run) {
	}

	private static final float CARD_H = 29, ROW_H = 18, PAD = 3, MENU_MIN_W = 156;

	private final List<Shortcut> shortcuts;
	private final Runnable onSwitched;
	private final Map<String, Component> heads = new HashMap<>();
	private boolean open;
	private float hover;
	private LauncherBridge.Accounts accounts;
	private String loadError;
	private boolean loading;

	public AccountCard(List<Shortcut> shortcuts, Runnable onSwitched) {
		this.shortcuts = List.copyOf(shortcuts);
		this.onSwitched = onSwitched;
	}

	public boolean isOpen() {
		return open;
	}

	public void close() {
		open = false;
	}

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	private static String userName() {
		return mc().getUser().getName();
	}

	private Component head(UUID id) {
		return heads.computeIfAbsent(id.toString(), k -> Component.literal(" ")
			.withStyle(s -> s.withFont(new FontDescription.PlayerSprite(ResolvableProfile.createUnresolved(id), true))));
	}

	public float width() {
		return Math.max(98, Fonts.width(userName(), Fonts.BOLD) + 50);
	}

	private float menuWidth() {
		float w = Math.max(width(), MENU_MIN_W);
		if (accounts != null) {
			for (LauncherBridge.Account a : accounts.list()) w = Math.max(w, Fonts.width(a.name(), Fonts.BODY) + 78);
		}
		return w;
	}

	// ------------------------------------------------------------------ drawing

	public void render(GuiGraphicsExtractor g, int screenW, int mouseX, int mouseY) {
		float cw = width(), cx = screenW - cw - 10, cy = 10;
		boolean hov = Ui.inside(mouseX, mouseY, cx, cy, cw, CARD_H) || open;
		hover = Ui.approach(hover, hov ? 1f : 0f, 14f);
		Ui.rect(g, cx, cy, cw, CARD_H, 6, Theme.lerp(0x99081226, 0xB8122244, hover));
		Ui.ring(g, cx, cy, cw, CARD_H, 6, Theme.lerp(0x669CC9FF, 0xCCCFE9FF, hover), 1.2f);
		User user = mc().getUser();
		drawHead(g, user.getProfileId(), cx + 7, cy + 6.5f, 2f);
		Ui.textMid(g, user.getName(), cx + 30, cy + 10.5f, 0xFFFFFFFF, Fonts.BOLD);
		String sub = AccountSwitcher.switchingTo() != null ? "Switching..." : SharedConstants.getCurrentVersion().name();
		Ui.textMid(g, sub, cx + 30, cy + 20, 0xFF9DB4D6, Fonts.SMALL);
		Ui.icon(g, open ? Icon.EXPAND_LESS : Icon.EXPAND, cx + cw - 11, cy + 11, 0xFFDDE8F8, Fonts.ICON_M);
	}

	private void drawHead(GuiGraphicsExtractor g, UUID id, float x, float y, float scale) {
		float size = 8 * scale;
		Ui.rect(g, x - 0.5f, y - 0.5f, size + 1, size + 1, Math.max(1, scale), 0x66000000);
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale);
		g.text(mc().font, head(id), 0, 0, 0xFFFFFFFF, false);
		g.pose().popMatrix();
	}

	/** Rows of the dropdown, top to bottom; rebuilt every frame from the current state. */
	private List<Row> rows() {
		List<Row> rows = new ArrayList<>();
		rows.add(Row.label("ACCOUNTS"));
		if (!LauncherBridge.available()) {
			rows.add(Row.note("Start Polar from the launcher"));
			rows.add(Row.note("to switch accounts here."));
		} else if (accounts == null) {
			rows.add(Row.note(loadError != null ? loadError : "Loading accounts..."));
		} else {
			for (LauncherBridge.Account a : accounts.list()) rows.add(Row.account(a));
			if (accounts.list().isEmpty()) rows.add(Row.note("No accounts in the launcher."));
		}
		if (AccountSwitcher.switchingTo() != null) rows.add(Row.note("Switching to " + AccountSwitcher.switchingTo() + "..."));
		else if (AccountSwitcher.lastError() != null) rows.add(Row.error(AccountSwitcher.lastError()));
		if (!shortcuts.isEmpty()) {
			rows.add(Row.divider());
			for (Shortcut s : shortcuts) rows.add(Row.shortcut(s));
		}
		return rows;
	}

	public void renderMenu(GuiGraphicsExtractor g, int screenW, int mouseX, int mouseY) {
		if (!open) return;
		List<Row> rows = rows();
		float mw = menuWidth(), mx = screenW - mw - 10, my = 10 + CARD_H + 4;
		float mh = PAD * 2;
		for (Row r : rows) mh += r.height();
		Ui.rect(g, mx, my, mw, mh, 7, 0xFF0B1528);
		Ui.ring(g, mx, my, mw, mh, 7, 0x809CC9FF, 1.2f);
		float y = my + PAD;
		for (Row r : rows) {
			float h = r.height();
			switch (r.kind) {
				case LABEL -> Ui.textSpaced(g, r.text, mx + 9, y + h / 2f + 1, 0xFF7F93B5, Fonts.SMALL, 0.8f);
				case NOTE -> Ui.textMid(g, Fonts.ellipsize(r.text, Fonts.SMALL, (int) (mw - 18)), mx + 9, y + h / 2f, 0xFF9DB4D6, Fonts.SMALL);
				case ERROR -> Ui.textMid(g, Fonts.ellipsize(r.text, Fonts.SMALL, (int) (mw - 18)), mx + 9, y + h / 2f, 0xFFFF7A7A, Fonts.SMALL);
				case DIVIDER -> Ui.hline(g, mx + 8, y + h / 2f, mw - 16, 1, 0x339CC9FF);
				case ACCOUNT -> {
					LauncherBridge.Account a = r.account;
					boolean current = AccountSwitcher.isCurrent(a);
					boolean hov = Ui.inside(mouseX, mouseY, mx + PAD, y, mw - 2 * PAD, h);
					if (current) Ui.rect(g, mx + PAD, y, mw - 2 * PAD, h, 4, 0x2E8CC8FF);
					else if (hov) Ui.rect(g, mx + PAD, y, mw - 2 * PAD, h, 4, 0x26FFFFFF);
					UUID id = parseOrNull(a.uuid());
					if (id != null) drawHead(g, id, mx + 9, y + 3, 1.5f);
					Ui.textMid(g, a.name(), mx + 26, y + h / 2f, 0xFFFFFFFF, Fonts.BODY);
					if (current) {
						Ui.icon(g, Icon.CHECK, mx + mw - 14, y + h / 2f, 0xFF8CC8FF, Fonts.ICON_M);
					} else if (a.offline()) {
						float bw = Fonts.width("OFFLINE", Fonts.SMALL) + 8;
						Ui.rect(g, mx + mw - 10 - bw, y + h / 2f - 4.5f, bw, 9, 4.5f, 0x268A9BBA);
						Ui.textMid(g, "OFFLINE", mx + mw - 6 - bw, y + h / 2f, 0xFF9DB4D6, Fonts.SMALL);
					}
				}
				case SHORTCUT -> {
					boolean hov = Ui.inside(mouseX, mouseY, mx + PAD, y, mw - 2 * PAD, h);
					if (hov) Ui.rect(g, mx + PAD, y, mw - 2 * PAD, h, 4, 0x26FFFFFF);
					Ui.icon(g, r.shortcut.icon(), mx + 13, y + h / 2f, 0xFFFFFFFF, Fonts.ICON_M);
					Ui.textMid(g, r.shortcut.label(), mx + 26, y + h / 2f, 0xFFFFFFFF, Fonts.BODY);
				}
			}
			y += h;
		}
	}

	// ------------------------------------------------------------------ input

	/** Returns true when the click was for the card or its menu (clicks elsewhere just close the menu). */
	public boolean mouseClicked(double mouseX, double mouseY, int screenW) {
		float cw = width(), cx = screenW - cw - 10;
		if (Ui.inside(mouseX, mouseY, cx, 10, cw, CARD_H)) {
			open = !open;
			if (open) reload();
			click();
			return true;
		}
		if (!open) return false;
		float mw = menuWidth(), mx = screenW - mw - 10, y = 10 + CARD_H + 4 + PAD;
		for (Row r : rows()) {
			float h = r.height();
			if (Ui.inside(mouseX, mouseY, mx + PAD, y, mw - 2 * PAD, h)) {
				if (r.kind == Kind.ACCOUNT && !AccountSwitcher.isCurrent(r.account)) {
					click();
					AccountSwitcher.switchTo(r.account, () -> {
						heads.clear();
						reload();
						onSwitched.run();
					});
					return true;
				}
				if (r.kind == Kind.SHORTCUT) {
					click();
					open = false;
					r.shortcut.run().run();
					return true;
				}
				return true; // labels, notes: keep the menu open
			}
			y += h;
		}
		open = false;
		return true;
	}

	private void reload() {
		if (!LauncherBridge.available() || loading) return;
		loading = true;
		loadError = null;
		AccountSwitcher.loadAccounts(a -> {
			accounts = a;
			loading = false;
		}, err -> {
			loadError = err;
			loading = false;
		});
	}

	private static void click() {
		mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
	}

	private static UUID parseOrNull(String raw) {
		String s = raw == null ? "" : raw.replace("-", "");
		if (!s.matches("[0-9a-fA-F]{32}")) return null;
		return UUID.fromString(s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16) + "-"
			+ s.substring(16, 20) + "-" + s.substring(20));
	}

	private enum Kind { LABEL, NOTE, ERROR, DIVIDER, ACCOUNT, SHORTCUT }

	private record Row(Kind kind, String text, LauncherBridge.Account account, Shortcut shortcut) {
		static Row label(String t) {
			return new Row(Kind.LABEL, t, null, null);
		}

		static Row note(String t) {
			return new Row(Kind.NOTE, t, null, null);
		}

		static Row error(String t) {
			return new Row(Kind.ERROR, t, null, null);
		}

		static Row divider() {
			return new Row(Kind.DIVIDER, "", null, null);
		}

		static Row account(LauncherBridge.Account a) {
			return new Row(Kind.ACCOUNT, a.name(), a, null);
		}

		static Row shortcut(Shortcut s) {
			return new Row(Kind.SHORTCUT, s.label(), null, s);
		}

		float height() {
			return switch (kind) {
				case LABEL -> 13;
				case NOTE, ERROR -> 11;
				case DIVIDER -> 7;
				case ACCOUNT, SHORTCUT -> ROW_H;
			};
		}
	}
}
