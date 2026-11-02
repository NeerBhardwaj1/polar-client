package dev.polarclient.modules;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.setting.NumberSetting;
import dev.polarclient.setting.TextSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Says "gg" automatically when a minigame ends (works with common end-of-game messages). */
public class AutoGGModule extends Module {
	private static final List<String> TRIGGERS = List.of(
		"1st Killer", "Reward Summary", "Winner: ", "Winners: ", "WINNER!", "VICTORY!",
		"Top Survivors", "won the game", "has won the game", "Game Over!"
	);
	private final TextSetting message = add(new TextSetting("message", "Message", "gg", 64));
	private final NumberSetting delay = add(new NumberSetting("delay", "Delay", 1, 0, 5, 0.5, "s"));

	private long sendAt = -1;
	private long lastSent;

	public AutoGGModule() {
		super("auto_gg", "Auto GG", "Sends \"gg\" when a game ends.", Category.UTILITY, false);
	}

	/** Server (system) messages only, so other players' chat can't trigger it. */
	public void onGameMessage(Component message, boolean overlay) {
		if (!isEnabled() || overlay) return;
		long now = System.currentTimeMillis();
		if (sendAt >= 0 || now - lastSent < 10_000) return;
		String text = message.getString();
		for (String t : TRIGGERS) {
			if (text.contains(t)) {
				sendAt = now + (long) (delay.get() * 1000);
				return;
			}
		}
	}

	@Override
	public void tick(Minecraft mc) {
		if (sendAt < 0 || System.currentTimeMillis() < sendAt) return;
		sendAt = -1;
		String msg = message.get().trim();
		if (isEnabled() && !msg.isEmpty() && mc.player != null && mc.getConnection() != null) {
			mc.getConnection().sendChat(msg);
			lastSent = System.currentTimeMillis();
		}
	}
}
