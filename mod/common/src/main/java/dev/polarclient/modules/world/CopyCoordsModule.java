package dev.polarclient.modules.world;

import dev.polarclient.module.Category;
import dev.polarclient.module.Module;
import dev.polarclient.platform.PolarKey;
import dev.polarclient.setting.BooleanSetting;
import dev.polarclient.setting.ModeSetting;
import dev.polarclient.setting.TextSetting;
import dev.polarclient.util.Popups;

/** A key (J) that copies your coordinates or sends them in chat. */
public class CopyCoordsModule extends Module {
	public final ModeSetting target = add(new ModeSetting("target", "Send to", "Clipboard", "Clipboard", "Chat", "Both"));
	public final ModeSetting format = add(new ModeSetting("format", "Format", "X, Y, Z", "X, Y, Z", "X Y Z", "X: Y: Z:"));
	public final BooleanSetting withDimension = add(new BooleanSetting("dimension", "Add the dimension", false));
	public final TextSetting prefix = add(new TextSetting("prefix", "Chat prefix", "My coords:", 40));

	public CopyCoordsModule() {
		super("copy_coordinates", "Coordinates Hotkey", "Press J to copy your coordinates or post them in chat (change the key in Controls).", Category.WORLD, true);
		prefix.visibleWhen(() -> !target.is("Clipboard"));
	}

	@Override
	public void tick() {
		while (p().consumePolarKey(PolarKey.COPY_COORDS)) {
			if (isEnabled() && p().hasPlayer()) run();
		}
	}

	public String text() {
		int x = (int) Math.floor(p().playerX()), y = (int) Math.floor(p().playerY()), z = (int) Math.floor(p().playerZ());
		String s;
		switch (format.get()) {
			case "X Y Z": s = x + " " + y + " " + z; break;
			case "X: Y: Z:": s = "X: " + x + " Y: " + y + " Z: " + z; break;
			default: s = x + ", " + y + ", " + z; break;
		}
		if (withDimension.on()) s += " (" + dimensionName(p().dimension()) + ")";
		return s;
	}

	static String dimensionName(String id) {
		if (id == null) return "?";
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		if (path.equals("overworld") || path.equals("0")) return "Overworld";
		if (path.equals("the_nether") || path.equals("-1")) return "Nether";
		if (path.equals("the_end") || path.equals("1")) return "End";
		return path.replace('_', ' ');
	}

	private void run() {
		String coords = text();
		boolean clip = !target.is("Chat"), chat = !target.is("Clipboard");
		if (clip) p().setClipboard(coords);
		if (chat) {
			String pre = prefix.get().trim();
			p().sendChat((pre.isEmpty() ? "" : pre + " ") + coords);
		}
		if (clip) Popups.show("Copied " + coords, 0xFF8CC8FF, 2000);
	}
}
