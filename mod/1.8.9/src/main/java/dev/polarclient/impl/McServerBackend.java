package dev.polarclient.impl;

import dev.polarclient.Log;
import dev.polarclient.platform.ServerBackend;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AddServerScreen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.DirectConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.LanServerQueryManager;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** 1.8.9's servers.dat, server pinger and LAN discovery, for Polar's server list. */
final class McServerBackend implements ServerBackend {
	private static final AtomicInteger THREADS = new AtomicInteger();
	private static final ExecutorService PINGERS = Executors.newFixedThreadPool(5, r -> {
		Thread t = new Thread(r, "Polar Server Pinger #" + THREADS.incrementAndGet());
		t.setDaemon(true);
		return t;
	});
	private static final Identifier UNKNOWN_ICON = new Identifier("textures/misc/unknown_server.png");
	private static final int PROTOCOL = 47;

	private final MinecraftClient mc = MinecraftClient.getInstance();
	private final Screen returnTo;
	private final MultiplayerServerListPinger pinger = new MultiplayerServerListPinger();
	private ServerList list;
	private final List<Entry> entries = new ArrayList<Entry>();
	private List<Lan> lan = Collections.emptyList();
	private final Map<LanServerQueryManager.LanServerInfo, LanEntry> lanCache = new IdentityHashMap<LanServerQueryManager.LanServerInfo, LanEntry>();
	private LanServerQueryManager.LanServerEntryList lanList;
	private LanServerQueryManager.LanServerDetector lanDetector;
	private ServerInfo editing;

	McServerBackend(Screen returnTo) {
		this.returnTo = returnTo;
	}

	/** One saved server: keeps its favicon texture between frames and tracks its ping. */
	private final class Entry implements Server {
		final ServerInfo data;
		final Identifier iconLocation;
		NativeImageBackedTexture icon;
		String lastIcon;
		volatile boolean started, failed;
		boolean closed;

		Entry(ServerInfo data) {
			this.data = data;
			this.iconLocation = new Identifier("polarclient", "servers/" + Integer.toHexString(data.address.hashCode()) + "/icon");
		}

		void startPing() {
			if (started) return;
			started = true;
			data.online = true;
			data.ping = -2L;
			data.label = "";
			data.playerCountLabel = "";
			PINGERS.submit(() -> {
				try {
					pinger.add(data);
				} catch (UnknownHostException e) {
					failed = true;
					data.ping = -1L;
					data.label = Formatting.DARK_RED + "Can't resolve hostname";
				} catch (Exception e) {
					failed = true;
					data.ping = -1L;
					data.label = Formatting.DARK_RED + "Can't connect to server.";
				}
			});
		}

		void updateIcon() {
			if (closed) return;
			String b64 = data.getIcon();
			if (Objects.equals(b64, lastIcon)) return;
			lastIcon = b64;
			if (b64 == null) {
				clearIcon();
				return;
			}
			try {
				BufferedImage image = ImageIO.read(new ByteArrayInputStream(Base64.getMimeDecoder().decode(b64)));
				if (image == null || image.getWidth() != 64 || image.getHeight() != 64) throw new IllegalArgumentException("Icon must be 64x64");
				if (icon == null) {
					icon = new NativeImageBackedTexture(image.getWidth(), image.getHeight());
					mc.getTextureManager().loadTexture(iconLocation, icon);
				}
				image.getRGB(0, 0, 64, 64, icon.getPixels(), 0, 64);
				icon.upload();
			} catch (Throwable t) {
				Log.warn("Invalid icon for server " + data.name);
				data.setIcon(null);
				lastIcon = null;
				clearIcon();
				list.saveFile();
			}
		}

		void clearIcon() {
			if (icon != null) {
				mc.getTextureManager().close(iconLocation);
				icon = null;
			}
		}

		void close() {
			clearIcon();
			closed = true;
		}

		@Override
		public String name() {
			return data.name;
		}

		@Override
		public String address() {
			return data.address;
		}

		@Override
		public Status status() {
			if (!started) return Status.INITIAL;
			if (failed) return Status.UNREACHABLE;
			if (data.ping >= 0) return data.protocolVersion == PROTOCOL ? Status.SUCCESSFUL : Status.INCOMPATIBLE;
			String label = data.label == null ? "" : Formatting.strip(data.label);
			if (label.startsWith("Can't")) return Status.UNREACHABLE;
			return Status.PINGING;
		}

		@Override
		public long ping() {
			return data.ping;
		}

		/** The ping only leaves "online/max" as text, so it's read back from that. */
		private int[] playerCount() {
			if (data.playerCountLabel == null) return null;
			String s = Formatting.strip(data.playerCountLabel);
			int slash = s.indexOf('/');
			if (slash <= 0) return null;
			try {
				return new int[] {Integer.parseInt(s.substring(0, slash).trim()), Integer.parseInt(s.substring(slash + 1).trim())};
			} catch (NumberFormatException e) {
				return null;
			}
		}

		@Override
		public boolean hasPlayerCount() {
			return playerCount() != null;
		}

		@Override
		public int online() {
			int[] c = playerCount();
			return c == null ? 0 : c[0];
		}

		@Override
		public int max() {
			int[] c = playerCount();
			return c == null ? 0 : c[1];
		}

		@Override
		public List<String> playerSample() {
			if (data.playerListSummary == null || data.playerListSummary.isEmpty()) return new ArrayList<String>();
			List<String> out = new ArrayList<String>();
			for (String line : data.playerListSummary.split("\n")) out.add(Formatting.strip(line));
			return out;
		}

		@Override
		public String versionName() {
			return data.version == null ? "" : Formatting.strip(data.version);
		}

		@Override
		public Object motd() {
			return data.label == null ? "" : data.label;
		}

		@Override
		public Object icon() {
			return icon != null ? iconLocation : UNKNOWN_ICON;
		}
	}

	private static final class LanEntry implements Lan {
		final LanServerQueryManager.LanServerInfo server;

		LanEntry(LanServerQueryManager.LanServerInfo server) {
			this.server = server;
		}

		@Override
		public String motd() {
			return server.getMotd();
		}

		@Override
		public String address() {
			return server.getAddressPort();
		}
	}

	@Override
	public void load() {
		// like vanilla, re-read servers.dat each time the list shows again (after Add/Edit/Delete) so pings restart
		closeIcons();
		pinger.cancel();
		list = new ServerList(mc);
		list.loadFile();
		entries.clear();
		for (int i = 0; i < list.size(); i++) entries.add(new Entry(list.get(i)));
		if (lanDetector == null) {
			lanList = new LanServerQueryManager.LanServerEntryList();
			lan = Collections.emptyList();
			lanCache.clear();
			try {
				lanDetector = new LanServerQueryManager.LanServerDetector(lanList);
				lanDetector.start();
			} catch (Exception e) {
				Log.warn("Unable to start LAN server detection", e);
			}
		}
	}

	@Override
	public List<Server> servers() {
		return Collections.unmodifiableList(new ArrayList<Server>(entries));
	}

	@Override
	public void ping(Server server) {
		if (!(server instanceof Entry)) return;
		Entry e = (Entry) server;
		e.startPing();
		e.updateIcon();
	}

	@Override
	public void swap(int i, int j) {
		if (list == null || i < 0 || j < 0 || i >= entries.size() || j >= entries.size()) return;
		list.swapEntries(i, j);
		list.saveFile();
		Collections.swap(entries, i, j);
	}

	@Override
	public void tick() {
		if (lanList != null && lanList.needsUpdate()) {
			List<Lan> next = new ArrayList<Lan>();
			Map<LanServerQueryManager.LanServerInfo, LanEntry> keep = new IdentityHashMap<LanServerQueryManager.LanServerInfo, LanEntry>();
			for (LanServerQueryManager.LanServerInfo s : lanList.getServers()) {
				LanEntry e = lanCache.containsKey(s) ? lanCache.get(s) : new LanEntry(s);
				keep.put(s, e);
				next.add(e);
			}
			lanList.markClean();
			lanCache.clear();
			lanCache.putAll(keep);
			lan = Collections.unmodifiableList(next);
		}
		pinger.tick();
	}

	@Override
	public List<Lan> lan() {
		return lan;
	}

	@Override
	public void close() {
		if (lanDetector != null) {
			lanDetector.interrupt();
			lanDetector = null;
		}
		pinger.cancel();
		closeIcons();
		entries.clear();
	}

	private void closeIcons() {
		for (Entry e : entries) e.close();
	}

	// ------------------------------------------------------------------ vanilla dialogs (they answer through ViewScreen#confirmResult)

	private void ask(Screen dialog, Consumer<Boolean> answer) {
		if (returnTo instanceof ViewScreen) ((ViewScreen) returnTo).onConfirm = answer;
		mc.setScreen(dialog);
	}

	@Override
	public void openAdd() {
		editing = new ServerInfo(I18n.translate("selectServer.defaultName"), "", false);
		ask(new AddServerScreen(returnTo, editing), ok -> {
			if (ok) {
				list.add(editing);
				list.saveFile();
			}
			mc.setScreen(returnTo);
		});
	}

	@Override
	public void openEdit(Server server) {
		if (!(server instanceof Entry)) return;
		final ServerInfo current = ((Entry) server).data;
		editing = new ServerInfo(current.name, current.address, false);
		editing.copyFrom(current);
		ask(new AddServerScreen(returnTo, editing), ok -> {
			if (ok) {
				current.name = editing.name;
				current.address = editing.address;
				current.copyFrom(editing);
				list.saveFile();
			}
			mc.setScreen(returnTo);
		});
	}

	@Override
	public void openDelete(Server server) {
		if (!(server instanceof Entry)) return;
		final ServerInfo data = ((Entry) server).data;
		final int index = entries.indexOf(server);
		String question = I18n.translate("selectServer.deleteQuestion");
		String warning = "'" + data.name + "' " + I18n.translate("selectServer.deleteWarning");
		ask(new ConfirmScreen(returnTo, question, warning, I18n.translate("selectServer.deleteButton"), I18n.translate("gui.cancel"), 0), ok -> {
			if (ok && index >= 0 && index < list.size()) {
				list.remove(index);
				list.saveFile();
			}
			mc.setScreen(returnTo);
		});
	}

	@Override
	public void openDirect() {
		editing = new ServerInfo(I18n.translate("selectServer.defaultName"), "", false);
		ask(new DirectConnectScreen(returnTo, editing), ok -> {
			if (ok) connect(editing);
			else mc.setScreen(returnTo);
		});
	}

	@Override
	public void join(Server server) {
		if (server instanceof Entry) connect(((Entry) server).data);
	}

	@Override
	public void joinLan(Lan lan) {
		if (lan instanceof LanEntry) {
			LanServerQueryManager.LanServerInfo s = ((LanEntry) lan).server;
			connect(new ServerInfo(s.getMotd(), s.getAddressPort(), true));
		}
	}

	private void connect(ServerInfo data) {
		close(); // the list reloads (and pings again) if the connection fails and you come back to it
		mc.setScreen(new ConnectScreen(returnTo, mc, data));
	}

}
