package dev.polarclient.account;

import dev.polarclient.Log;
import dev.polarclient.platform.Platform;

import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * Switches the game to another of the launcher's accounts without restarting (each version swaps its own session
 * objects in {@link Platform#switchSession}). Only from menus, never in a world.
 */
public final class AccountSwitcher {
	private static volatile String switchingTo;
	private static volatile String lastError;

	private AccountSwitcher() {
	}

	/** Name of the account being switched to, or null. */
	public static String switchingTo() {
		return switchingTo;
	}

	/** Message from the last failed switch, or null. */
	public static String lastError() {
		return lastError;
	}

	public static boolean isCurrent(LauncherBridge.Account account) {
		UUID id = parse(account.uuid());
		return id != null && id.equals(Platform.get().userId());
	}

	/** Starts a switch; {@code onDone} runs on the game thread when it worked. */
	public static void switchTo(LauncherBridge.Account account, Runnable onDone) {
		final Platform p = Platform.get();
		if (switchingTo != null) return;
		if (p.inWorld()) {
			lastError = "Leave your world before switching accounts.";
			return;
		}
		switchingTo = account.name();
		lastError = null;
		LauncherBridge.session(account.uuid()).whenComplete((session, err) -> p.execute(() -> {
			try {
				if (err != null) throw err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
				if (parse(session.uuid()) == null || session.name().isEmpty() || session.accessToken().isEmpty()) {
					throw new IllegalStateException("The launcher sent an incomplete account.");
				}
				p.switchSession(session);
				Log.info("Switched account to " + session.name());
				onDone.run();
			} catch (Throwable t) {
				Log.warn("Account switch failed", t);
				lastError = t.getMessage() == null ? "Couldn't switch accounts." : t.getMessage();
			} finally {
				switchingTo = null;
			}
		}));
	}

	/** Accepts both dashed and undashed UUIDs. */
	public static UUID parse(String raw) {
		if (raw == null) return null;
		String s = raw.replace("-", "");
		if (!s.matches("[0-9a-fA-F]{32}")) return null;
		return UUID.fromString(s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16) + "-"
			+ s.substring(16, 20) + "-" + s.substring(20));
	}

	/** Small helper for screens: fetch the account list and hand it over on the game thread. */
	public static void loadAccounts(Consumer<LauncherBridge.Accounts> onLoaded, Consumer<String> onError) {
		final Platform p = Platform.get();
		LauncherBridge.accounts().whenComplete((a, err) -> p.execute(() -> {
			if (err == null) onLoaded.accept(a);
			else onError.accept("Couldn't reach the Polar launcher.");
		}));
	}
}
