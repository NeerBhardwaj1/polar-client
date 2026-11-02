package dev.polarclient.impl;

import com.mojang.blaze3d.platform.NativeImage;
import dev.polarclient.Log;
import dev.polarclient.platform.ServerBackend;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.LanServer;
import net.minecraft.client.server.LanServerDetection;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** 1.21.10's servers.dat, server pinger and LAN discovery, for Polar's server list. */
final class McServerBackend implements ServerBackend {
	private static final AtomicInteger THREADS = new AtomicInteger();
	private static final ExecutorService PINGERS = Executors.newFixedThreadPool(5, r -> {
		Thread t = new Thread(r, "Polar Server Pinger #" + THREADS.incrementAndGet());
		t.setDaemon(true);
		return t;
	});

	private final Minecraft mc = Minecraft.getInstance();
	private final Screen returnTo;
	private final ServerStatusPinger pinger = new ServerStatusPinger();
	private ServerList list;
	private final List<Entry> entries = new ArrayList<>();
	private List<Lan> lan = Collections.emptyList();
	private final Map<LanServer, LanEntry> lanCache = new IdentityHashMap<>();
	private LanServerDetection.LanServerList lanList;
	private LanServerDetection.LanServerDetector lanDetector;
	private ServerData editing;

	McServerBackend(Screen returnTo) {
		this.returnTo = returnTo;
	}

	/** One saved server: keeps its favicon texture between frames. */
	private final class Entry implements Server {
		final ServerData data;
		final FaviconTexture icon;
		byte[] lastIconBytes;

		Entry(ServerData data) {
			this.data = data;
			this.icon = FaviconTexture.forServer(mc.getTextureManager(), data.ip);
		}

		void startPing() {
			if (data.state() != ServerData.State.INITIAL) return;
			data.setState(ServerData.State.PINGING);
			data.motd = CommonComponents.EMPTY;
			data.status = CommonComponents.EMPTY;
			final ServerList saveTo = list;
			PINGERS.submit(() -> {
				try {
					pinger.pingServer(data, () -> mc.execute(saveTo::save), () -> data.setState(
						data.protocol == SharedConstants.getCurrentVersion().protocolVersion() ? ServerData.State.SUCCESSFUL : ServerData.State.INCOMPATIBLE));
				} catch (UnknownHostException e) {
					data.setState(ServerData.State.UNREACHABLE);
					data.motd = Component.translatable("multiplayer.status.cannot_resolve").withStyle(ChatFormatting.RED);
				} catch (Exception e) {
					data.setState(ServerData.State.UNREACHABLE);
					data.motd = Component.translatable("multiplayer.status.cannot_connect").withStyle(ChatFormatting.RED);
				}
			});
		}

		void updateIcon() {
			if (icon.isClosed()) return;
			byte[] bytes = data.getIconBytes();
			if (Arrays.equals(bytes, lastIconBytes)) return;
			try {
				if (bytes == null) icon.clear();
				else icon.upload(NativeImage.read(bytes));
				lastIconBytes = bytes;
			} catch (Throwable t) {
				Log.warn("Invalid icon for server " + data.name);
				data.setIconBytes(null);
				lastIconBytes = null;
				list.save();
			}
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
			switch (data.state()) {
				case PINGING: return Status.PINGING;
				case SUCCESSFUL: return Status.SUCCESSFUL;
				case INCOMPATIBLE: return Status.INCOMPATIBLE;
				case UNREACHABLE: return Status.UNREACHABLE;
				default: return Status.INITIAL;
			}
		}

		@Override
		public long ping() {
			return data.ping;
		}

		@Override
		public boolean hasPlayerCount() {
			return data.players != null;
		}

		@Override
		public int online() {
			return data.players == null ? 0 : data.players.online();
		}

		@Override
		public int max() {
			return data.players == null ? 0 : data.players.max();
		}

		@Override
		public List<String> playerSample() {
			List<String> out = new ArrayList<>();
			for (Component c : data.playerList) out.add(c.getString());
			return out;
		}

		@Override
		public String versionName() {
			return data.version.getString();
		}

		@Override
		public Object motd() {
			return data.motd;
		}

		@Override
		public Object icon() {
			return icon.textureLocation();
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
		rebuild();
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

	private void rebuild() {
		entries.clear();
		for (int i = 0; i < list.size(); i++) entries.add(new Entry(list.get(i)));
	}

	@Override
	public List<Server> servers() {
		return Collections.unmodifiableList(new ArrayList<Server>(entries));
	}

	@Override
	public void ping(Server server) {
		if (!(server instanceof Entry e)) return;
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
		List<LanServer> found = lanList == null ? null : lanList.takeDirtyServers();
		if (found != null) {
			List<Lan> next = new ArrayList<>();
			Map<LanServer, LanEntry> keep = new IdentityHashMap<>();
			for (LanServer s : found) {
				LanEntry e = lanCache.containsKey(s) ? lanCache.get(s) : new LanEntry(s);
				keep.put(s, e);
				next.add(e);
			}
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
		for (Entry e : entries) {
			if (!e.icon.isClosed()) e.icon.close();
		}
	}

	// ------------------------------------------------------------------ vanilla dialogs

	@Override
	public void openAdd() {
		editing = new ServerData("", "", ServerData.Type.OTHER);
		mc.setScreen(new ManageServerScreen(returnTo, Component.translatable("manageServer.add.title"), ok -> {
			if (ok) {
				ServerData hidden = list.unhide(editing.ip);
				if (hidden != null) hidden.copyNameIconFrom(editing);
				else list.add(editing, false);
				list.save();
			}
			mc.setScreen(returnTo);
		}, editing));
	}

	@Override
	public void openEdit(Server server) {
		if (!(server instanceof Entry e)) return;
		ServerData current = e.data;
		editing = new ServerData(current.name, current.ip, ServerData.Type.OTHER);
		editing.copyFrom(current);
		mc.setScreen(new ManageServerScreen(returnTo, Component.translatable("manageServer.edit.title"), ok -> {
			if (ok) {
				current.name = editing.name;
				current.ip = editing.ip;
				current.setState(ServerData.State.INITIAL);
				current.setResourcePackStatus(editing.getResourcePackStatus());
				list.save();
			}
			mc.setScreen(returnTo);
		}, editing));
	}

	@Override
	public void openDelete(Server server) {
		if (!(server instanceof Entry e)) return;
		mc.setScreen(new ConfirmScreen(ok -> {
			if (ok) {
				list.remove(e.data);
				list.save();
			}
			mc.setScreen(returnTo);
		}, Component.translatable("selectServer.deleteQuestion"), Component.translatable("selectServer.deleteWarning", e.data.name),
			Component.translatable("selectServer.deleteButton"), CommonComponents.GUI_CANCEL));
	}

	@Override
	public void openDirect() {
		editing = new ServerData(I18n.get("selectServer.defaultName"), "", ServerData.Type.OTHER);
		mc.setScreen(new DirectJoinServerScreen(returnTo, ok -> {
			if (!ok) {
				mc.setScreen(returnTo);
				return;
			}
			ServerData existing = list.get(editing.ip);
			if (existing == null) {
				list.add(editing, true);
				list.save();
				connect(editing);
			} else {
				connect(existing);
			}
		}, editing));
	}

	@Override
	public void join(Server server) {
		if (server instanceof Entry e) connect(e.data);
	}

	@Override
	public void joinLan(Lan lan) {
		if (lan instanceof LanEntry l) connect(new ServerData(l.server.getMotd(), l.server.getAddress(), ServerData.Type.LAN));
	}

	private void connect(ServerData data) {
		close(); // the list reloads (and pings again) if the connection fails and you come back to it
		ConnectScreen.startConnecting(returnTo, mc, ServerAddress.parseString(data.ip), data, false, null);
	}
}
