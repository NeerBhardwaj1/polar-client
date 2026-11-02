package dev.polarclient.impl;

import com.google.common.hash.Hashing;
import com.mojang.blaze3d.platform.NativeImage;
import dev.polarclient.Log;
import dev.polarclient.platform.ServerBackend;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.EditServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.LanServer;
import net.minecraft.client.server.LanServerDetection;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** 1.16.5's servers.dat, server pinger and LAN discovery, for Polar's server list. */
final class McServerBackend implements ServerBackend {
	private static final AtomicInteger THREADS = new AtomicInteger();
	private static final ExecutorService PINGERS = Executors.newFixedThreadPool(5, r -> {
		Thread t = new Thread(r, "Polar Server Pinger #" + THREADS.incrementAndGet());
		t.setDaemon(true);
		return t;
	});
	private static final ResourceLocation UNKNOWN_ICON = new ResourceLocation("textures/misc/unknown_server.png");

	private final Minecraft mc = Minecraft.getInstance();
	private final Screen returnTo;
	private final ServerStatusPinger pinger = new ServerStatusPinger();
	private ServerList list;
	private final List<Entry> entries = new ArrayList<Entry>();
	private List<Lan> lan = Collections.emptyList();
	private final Map<LanServer, LanEntry> lanCache = new IdentityHashMap<LanServer, LanEntry>();
	private LanServerDetection.LanServerList lanList;
	private LanServerDetection.LanServerDetector lanDetector;
	private ServerData editing;

	McServerBackend(Screen returnTo) {
		this.returnTo = returnTo;
	}

	/** One saved server: keeps its favicon texture between frames and tracks its ping. */
	private final class Entry implements Server {
		final ServerData data;
		final ResourceLocation iconLocation;
		DynamicTexture icon;
		String lastIcon;
		volatile boolean started, failed;
		boolean closed;

		Entry(ServerData data) {
			this.data = data;
			this.iconLocation = new ResourceLocation("servers/" + Hashing.sha1().hashUnencodedChars(data.ip) + "/icon");
		}

		void startPing() {
			if (started) return;
			started = true;
			data.pinged = true;
			data.ping = -2L;
			data.motd = TextComponent.EMPTY;
			data.status = TextComponent.EMPTY;
			final ServerList saveTo = list;
			PINGERS.submit(() -> {
				try {
					pinger.pingServer(data, () -> mc.execute(saveTo::save));
				} catch (UnknownHostException e) {
					failed = true;
					data.ping = -1L;
					data.motd = new TranslatableComponent("multiplayer.status.cannot_resolve").withStyle(ChatFormatting.RED);
				} catch (Exception e) {
					failed = true;
					data.ping = -1L;
					data.motd = new TranslatableComponent("multiplayer.status.cannot_connect").withStyle(ChatFormatting.RED);
				}
			});
		}

		void updateIcon() {
			if (closed) return;
			String b64 = data.getIconB64();
			if (Objects.equals(b64, lastIcon)) return;
			lastIcon = b64;
			if (b64 == null) {
				clearIcon();
				return;
			}
			try {
				NativeImage image = NativeImage.fromBase64(b64);
				if (image.getWidth() != 64 || image.getHeight() != 64) {
					image.close();
					throw new IllegalArgumentException("Icon must be 64x64");
				}
				if (icon == null) icon = new DynamicTexture(image);
				else {
					icon.setPixels(image);
					icon.upload();
				}
				mc.getTextureManager().register(iconLocation, icon);
			} catch (Throwable t) {
				Log.warn("Invalid icon for server " + data.name);
				data.setIconB64(null);
				lastIcon = null;
				clearIcon();
				list.save();
			}
		}

		void clearIcon() {
			if (icon != null) {
				mc.getTextureManager().release(iconLocation);
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
			return data.ip;
		}

		@Override
		public Status status() {
			if (!started) return Status.INITIAL;
			if (failed) return Status.UNREACHABLE;
			if (data.ping >= 0) {
				return data.protocol == SharedConstants.getCurrentVersion().getProtocolVersion() ? Status.SUCCESSFUL : Status.INCOMPATIBLE;
			}
			if (data.motd instanceof TranslatableComponent && ((TranslatableComponent) data.motd).getKey().startsWith("multiplayer.status.cannot")) {
				return Status.UNREACHABLE;
			}
			return Status.PINGING;
		}

		@Override
		public long ping() {
			return data.ping;
		}

		/** The ping only leaves "online/max" as text, so it's read back from that. */
		private int[] playerCount() {
			if (data.status == null) return null;
			String s = data.status.getString();
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
			List<String> out = new ArrayList<String>();
			if (data.playerList != null) {
				for (Component c : data.playerList) out.add(c.getString());
			}
			return out;
		}

		@Override
		public String versionName() {
			return data.version == null ? "" : data.version.getString();
		}

		@Override
		public Object motd() {
			return data.motd;
		}

		@Override
		public Object icon() {
			return icon != null ? iconLocation : UNKNOWN_ICON;
		}
	}

	private static final class LanEntry implements Lan {
		final LanServer server;

		LanEntry(LanServer server) {
			this.server = server;
		}

		@Override
		public String motd() {
			return server.getMotd();
		}

		@Override
		public String address() {
			return server.getAddress();
		}
	}

	@Override
	public void load() {
		// like vanilla, re-read servers.dat each time the list shows again (after Add/Edit/Delete) so pings restart
		closeIcons();
		pinger.removeAll();
		list = new ServerList(mc);
		list.load();
		entries.clear();
		for (int i = 0; i < list.size(); i++) entries.add(new Entry(list.get(i)));
		if (lanDetector == null) {
			lanList = new LanServerDetection.LanServerList();
			lan = Collections.emptyList();
			lanCache.clear();
			try {
				lanDetector = new LanServerDetection.LanServerDetector(lanList);
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
		list.swap(i, j);
		list.save();
		Collections.swap(entries, i, j);
	}

	@Override
	public void tick() {
		if (lanList != null && lanList.isDirty()) {
			List<Lan> next = new ArrayList<Lan>();
			Map<LanServer, LanEntry> keep = new IdentityHashMap<LanServer, LanEntry>();
			for (LanServer s : lanList.getServers()) {
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
		pinger.removeAll();
		closeIcons();
		entries.clear();
	}

	private void closeIcons() {
		for (Entry e : entries) e.close();
	}

	// ------------------------------------------------------------------ vanilla dialogs

	@Override
	public void openAdd() {
		editing = new ServerData(I18n.get("selectServer.defaultName"), "", false);
		mc.setScreen(new EditServerScreen(returnTo, ok -> {
			if (ok) {
				list.add(editing);
				list.save();
			}
			mc.setScreen(returnTo);
		}, editing));
	}

	@Override
	public void openEdit(Server server) {
		if (!(server instanceof Entry)) return;
		final ServerData current = ((Entry) server).data;
		editing = new ServerData(current.name, current.ip, false);
		editing.copyFrom(current);
		mc.setScreen(new EditServerScreen(returnTo, ok -> {
			if (ok) {
				current.name = editing.name;
				current.ip = editing.ip;
				current.copyFrom(editing);
				list.save();
			}
			mc.setScreen(returnTo);
		}, editing));
	}

	@Override
	public void openDelete(Server server) {
		if (!(server instanceof Entry)) return;
		final ServerData data = ((Entry) server).data;
		mc.setScreen(new ConfirmScreen(ok -> {
			if (ok) {
				list.remove(data);
				list.save();
			}
			mc.setScreen(returnTo);
		}, new TranslatableComponent("selectServer.deleteQuestion"), new TranslatableComponent("selectServer.deleteWarning", data.name),
			new TranslatableComponent("selectServer.deleteButton"), CommonComponents.GUI_CANCEL));
	}

	@Override
	public void openDirect() {
		editing = new ServerData(I18n.get("selectServer.defaultName"), "", false);
		mc.setScreen(new DirectJoinServerScreen(returnTo, ok -> {
			if (ok) connect(editing);
			else mc.setScreen(returnTo);
		}, editing));
	}

	@Override
	public void join(Server server) {
		if (server instanceof Entry) connect(((Entry) server).data);
	}

	@Override
	public void joinLan(Lan lan) {
		if (lan instanceof LanEntry) {
			LanServer s = ((LanEntry) lan).server;
			connect(new ServerData(s.getMotd(), s.getAddress(), true));
		}
	}

	private void connect(ServerData data) {
		close(); // the list reloads (and pings again) if the connection fails and you come back to it
		mc.setScreen(new ConnectScreen(returnTo, mc, data));
	}
}
