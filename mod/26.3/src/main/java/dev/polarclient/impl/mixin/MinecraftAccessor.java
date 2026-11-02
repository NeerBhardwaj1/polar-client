package dev.polarclient.impl.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.services.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

/** Lets the account switcher replace the signed-in session (the same fields Minecraft fills in at startup). */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Mutable
	@Accessor("user")
	void polar$setUser(User user);

	@Mutable
	@Accessor("userApiService")
	void polar$setUserApiService(UserApiService service);

	@Mutable
	@Accessor("userPropertiesFuture")
	void polar$setUserPropertiesFuture(CompletableFuture<UserApiService.UserProperties> future);

	@Mutable
	@Accessor("profileFuture")
	void polar$setProfileFuture(CompletableFuture<ProfileResult> future);

	@Mutable
	@Accessor("profileKeyPairManager")
	void polar$setProfileKeyPairManager(ProfileKeyPairManager manager);

	@Accessor("reportingContext")
	void polar$setReportingContext(ReportingContext context);
}
