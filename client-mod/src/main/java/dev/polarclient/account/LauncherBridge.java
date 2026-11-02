package dev.polarclient.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Talks to the Polar launcher that started the game (a localhost-only endpoint it opens for this game session).
 * The address and secret arrive in the POLAR_BRIDGE environment variable; without it (e.g. a dev run) there's no bridge.
 */
public final class LauncherBridge {
	public record Account(String uuid, String name, String type) {
		public boolean offline() {
			return "offline".equals(type);
		}
	}

	public record Accounts(String selected, List<Account> list) {
	}

	public record Session(String uuid, String name, String type, String accessToken, String xuid) {
	}

	private static final String BASE;
	private static final String SECRET;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

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

	public static CompletableFuture<Accounts> accounts() {
		if (!available()) return CompletableFuture.failedFuture(new IllegalStateException("Not started from the Polar launcher"));
		HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/accounts"))
			.timeout(Duration.ofSeconds(5)).header("x-polar-token", SECRET).GET().build();
		return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
			JsonObject o = check(res);
			List<Account> list = new ArrayList<>();
			JsonArray arr = o.getAsJsonArray("accounts");
			if (arr != null) {
				for (JsonElement e : arr) {
					JsonObject a = e.getAsJsonObject();
					list.add(new Account(str(a, "uuid"), str(a, "name"), str(a, "type")));
				}
			}
			return new Accounts(str(o, "selected"), List.copyOf(list));
		});
	}

	/** Asks the launcher for a fresh session (it refreshes Microsoft tokens as needed) and marks the account selected. */
	public static CompletableFuture<Session> session(String uuid) {
		if (!available()) return CompletableFuture.failedFuture(new IllegalStateException("Not started from the Polar launcher"));
		JsonObject body = new JsonObject();
		body.addProperty("uuid", uuid);
		HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/session"))
			.timeout(Duration.ofSeconds(30)).header("x-polar-token", SECRET).header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
		return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
			JsonObject o = check(res);
			return new Session(str(o, "uuid"), str(o, "name"), str(o, "type"), str(o, "accessToken"), str(o, "xuid"));
		});
	}

	private static JsonObject check(HttpResponse<String> res) {
		JsonObject o;
		try {
			o = JsonParser.parseString(res.body()).getAsJsonObject();
		} catch (RuntimeException e) {
			throw new IllegalStateException("The launcher sent an unexpected reply");
		}
		if (res.statusCode() != 200) {
			String err = str(o, "error");
			throw new IllegalStateException(err.isEmpty() ? "The launcher refused (" + res.statusCode() + ")" : err);
		}
		return o;
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}
}
