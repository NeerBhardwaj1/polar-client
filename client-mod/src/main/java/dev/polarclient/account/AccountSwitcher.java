package dev.polarclient.account;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.services.MinecraftServicesDiscoveryService;
import com.mojang.authlib.services.ProfileResult;
import dev.polarclient.PolarClient;
import dev.polarclient.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.util.Util;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Switches the game to another of the launcher's accounts without restarting, by swapping the signed-in session
 * (user, services, chat-signing keys) the way Minecraft sets them up at startup. Only from menus, never in a world.
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
		return id != null && id.equals(Minecraft.getInstance().getUser().getProfileId());
	}

	/** Starts a switch; {@code onDone} runs on the render thread when it worked. */
	public static void switchTo(LauncherBridge.Account account, Runnable onDone) {
		Minecraft mc = Minecraft.getInstance();
		if (switchingTo != null) return;
		if (mc.level != null) {
			lastError = "Leave your world before switching accounts.";
			return;
		}
		switchingTo = account.name();
		lastError = null;
		LauncherBridge.session(account.uuid()).whenComplete((session, err) -> mc.execute(() -> {
			try {
				if (err != null) throw err instanceof RuntimeException r && r.getCause() != null ? r.getCause() : err;
				apply(mc, session);
				onDone.run();
			} catch (Throwable t) {
				PolarClient.LOGGER.warn("Account switch failed", t);
				lastError = t.getMessage() == null ? "Couldn't switch accounts." : t.getMessage();
			} finally {
				switchingTo = null;
			}
		}));
	}

	private static void apply(Minecraft mc, LauncherBridge.Session s) {
		UUID id = parse(s.uuid());
		if (id == null || s.name().isEmpty() || s.accessToken().isEmpty()) throw new IllegalStateException("The launcher sent an incomplete account.");
		boolean offline = "offline".equals(s.type());
		User old = mc.getUser();
		Optional<String> xuid = s.xuid().isEmpty() || "0".equals(s.xuid()) ? Optional.empty() : Optional.of(s.xuid());
		User user = new User(s.name(), id, s.accessToken(), xuid, old.getClientId());

		UserApiService api = offline ? UserApiService.OFFLINE
			: MinecraftServicesDiscoveryService.create(mc.getProxy(), true).createUserApiService(s.accessToken());
		MinecraftAccessor access = (MinecraftAccessor) mc;
		access.polar$setUser(user);
		access.polar$setUserApiService(api);
		access.polar$setUserPropertiesFuture(CompletableFuture.supplyAsync(() -> {
			try {
				return api.fetchProperties();
			} catch (AuthenticationException e) {
				return UserApiService.OFFLINE_PROPERTIES;
			}
		}, Util.nonCriticalIoPool()));
		CompletableFuture<ProfileResult> profile = offline ? CompletableFuture.completedFuture(null)
			: CompletableFuture.supplyAsync(() -> mc.services().sessionService().fetchProfile(id, true), Util.nonCriticalIoPool());
		access.polar$setProfileFuture(profile);
		access.polar$setProfileKeyPairManager(offline ? ProfileKeyPairManager.EMPTY_KEY_MANAGER
			: ProfileKeyPairManager.create(api, user, mc.gameDirectory.toPath()));
		access.polar$setReportingContext(ReportingContext.create(ReportEnvironment.local(), api));
		PolarClient.LOGGER.info("Switched account to {}", s.name());
	}

	/** Accepts both dashed and undashed UUIDs. */
	static UUID parse(String raw) {
		if (raw == null) return null;
		String s = raw.replace("-", "");
		if (!s.matches("[0-9a-fA-F]{32}")) return null;
		return UUID.fromString(s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16) + "-"
			+ s.substring(16, 20) + "-" + s.substring(20));
	}

	/** Small helper for screens: fetch the account list and hand it over on the render thread. */
	public static void loadAccounts(Consumer<LauncherBridge.Accounts> onLoaded, Consumer<String> onError) {
		Minecraft mc = Minecraft.getInstance();
		LauncherBridge.accounts().whenComplete((a, err) -> mc.execute(() -> {
			if (err == null) onLoaded.accept(a);
			else onError.accept("Couldn't reach the Polar launcher.");
		}));
	}
}
