package dev.polarclient.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Talks to the Polar launcher that started the game (a localhost-only endpoint it opens for this game session).
 * The address and secret arrive in the POLAR_BRIDGE environment variable; without it (e.g. a dev run) there's no bridge.
 */
public final class LauncherBridge {
	public static final class Account {
		private final String uuid, name, type;

		public Account(String uuid, String name, String type) {
			this.uuid = uuid;
			this.name = name;
			this.type = type;
		}

		public String uuid() {
			return uuid;
		}

		public String name() {
			return name;
		}

		public String type() {
			return type;
		}

		public boolean offline() {
			return "offline".equals(type);
		}
	}

	public static final class Accounts {
		private final String selected;
		private final List<Account> list;

		public Accounts(String selected, List<Account> list) {
			this.selected = selected;
			this.list = list;
		}

		public String selected() {
			return selected;
		}

		public List<Account> list() {
			return list;
		}
	}

	public static final class Session {
		private final String uuid, name, type, accessToken, xuid;

		public Session(String uuid, String name, String type, String accessToken, String xuid) {
			this.uuid = uuid;
			this.name = name;
			this.type = type;
			this.accessToken = accessToken;
			this.xuid = xuid;
		}

		public String uuid() {
			return uuid;
		}

		public String name() {
			return name;
		}

		public String type() {
			return type;
		}

		public String accessToken() {
			return accessToken;
		}

		public String xuid() {
			return xuid;
		}

		public boolean offline() {
			return "offline".equals(type);
		}
	}

	private static final String BASE;
	private static final String SECRET;
	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Polar Launcher Bridge");
		t.setDaemon(true);
		return t;
	});

	static {
		String env = System.getenv("POLAR_BRIDGE");
		int colon = env == null ? -1 : env.indexOf(':');
		if (colon > 0 && env.substring(0, colon).matches("\\d{1,5}")) {
			BASE = "http://127.0.0.1:" + env.substring(0, colon);
			SECRET = env.substring(colon + 1);
		} else {
			BASE = null;
			SECRET = null;
		}
	}

	private LauncherBridge() {
	}

	/** True when the game was started by the Polar launcher. */
	public static boolean available() {
		return BASE != null;
	}

	private static <T> CompletableFuture<T> failed(Throwable t) {
		CompletableFuture<T> f = new CompletableFuture<T>();
		f.completeExceptionally(t);
		return f;
	}

	public static CompletableFuture<Accounts> accounts() {
		if (!available()) return failed(new IllegalStateException("Not started from the Polar launcher"));
		return CompletableFuture.supplyAsync(() -> {
			JsonObject o = request("GET", "/accounts", null, 5000);
			List<Account> list = new ArrayList<Account>();
			JsonElement arrEl = o.get("accounts");
			if (arrEl != null && arrEl.isJsonArray()) {
				JsonArray arr = arrEl.getAsJsonArray();
				for (JsonElement e : arr) {
					JsonObject a = e.getAsJsonObject();
					list.add(new Account(str(a, "uuid"), str(a, "name"), str(a, "type")));
				}
			}
			return new Accounts(str(o, "selected"), Collections.unmodifiableList(list));
		}, IO);
	}

	/** Asks the launcher for a fresh session (it refreshes Microsoft tokens as needed) and marks the account selected. */
	public static CompletableFuture<Session> session(String uuid) {
		if (!available()) return failed(new IllegalStateException("Not started from the Polar launcher"));
		JsonObject body = new JsonObject();
		body.addProperty("uuid", uuid);
		return CompletableFuture.supplyAsync(() -> {
			JsonObject o = request("POST", "/session", body.toString(), 30000);
			return new Session(str(o, "uuid"), str(o, "name"), str(o, "type"), str(o, "accessToken"), str(o, "xuid"));
		}, IO);
	}

	/**
	 * Tells the launcher where you are, for its Discord status: "menu", "singleplayer", "server" (with the address, or
	 * null to keep it private) or "hidden". Fire and forget: nothing happens when the launcher isn't there.
	 */
	public static void presence(String state, String server) {
		if (!available()) return;
		final JsonObject body = new JsonObject();
		body.addProperty("state", state);
		if (server != null) body.addProperty("server", server);
		IO.execute(new Runnable() {
			@Override
			public void run() {
				try {
					request("POST", "/presence", body.toString(), 3000);
				} catch (RuntimeException ignored) {
					// the launcher was closed; Discord just keeps the last status
				}
			}
		});
	}

	/**
	 * A JSON call to the launcher's Polar endpoints (/polar/state, /polar/players, /polar/events), on the caller's
	 * thread. Throws IllegalStateException with a readable message when it fails.
	 */
	public static JsonObject polar(String method, String path, String body, int timeoutMs) {
		if (!available()) throw new IllegalStateException("Not started from the Polar launcher");
		return request(method, path, body, timeoutMs);
	}

	/** A cosmetics file kept by the launcher (catalog.json, models/..., textures/...), on the caller's thread. */
	public static byte[] polarFile(String path, int timeoutMs) {
		if (!available()) throw new IllegalStateException("Not started from the Polar launcher");
		try {
			HttpURLConnection c = (HttpURLConnection) new URL(BASE + "/polar/file?path=" + java.net.URLEncoder.encode(path, "UTF-8")).openConnection();
			c.setConnectTimeout(3000);
			c.setReadTimeout(timeoutMs);
			c.setRequestProperty("x-polar-token", SECRET);
			int status = c.getResponseCode();
			InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
			if (in == null) throw new IllegalStateException("No reply");
			ByteArrayOutputStream b = new ByteArrayOutputStream();
			try {
				byte[] buf = new byte[16384];
				int n;
				while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
			} finally {
				in.close();
			}
			if (status != 200) throw new IllegalStateException("The launcher couldn't get " + path + " (" + status + ")");
			return b.toByteArray();
		} catch (IllegalStateException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Couldn't reach the Polar launcher.");
		}
	}

	@SuppressWarnings("deprecation")
	private static JsonObject request(String method, String path, String body, int timeoutMs) {
		int status;
		String text;
		try {
			HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
			c.setRequestMethod(method);
			c.setConnectTimeout(3000);
			c.setReadTimeout(timeoutMs);
			c.setRequestProperty("x-polar-token", SECRET);
			if (body != null) {
				c.setDoOutput(true);
				c.setRequestProperty("Content-Type", "application/json");
				OutputStream out = c.getOutputStream();
				try {
					out.write(body.getBytes(StandardCharsets.UTF_8));
				} finally {
					out.close();
				}
			}
			status = c.getResponseCode();
			InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
			text = in == null ? "" : readAll(in);
		} catch (Exception e) {
			throw new IllegalStateException("Couldn't reach the Polar launcher.");
		}
		JsonObject o;
		try {
			o = new JsonParser().parse(text).getAsJsonObject();
		} catch (RuntimeException e) {
			throw new IllegalStateException("The launcher sent an unexpected reply");
		}
		if (status != 200) {
			String err = str(o, "error");
			throw new IllegalStateException(err.isEmpty() ? "The launcher refused (" + status + ")" : err);
		}
		return o;
	}

	private static String readAll(InputStream in) throws java.io.IOException {
		try {
			ByteArrayOutputStream b = new ByteArrayOutputStream();
			byte[] buf = new byte[4096];
			int n;
			while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
			return new String(b.toByteArray(), StandardCharsets.UTF_8);
		} finally {
			in.close();
		}
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}
}
