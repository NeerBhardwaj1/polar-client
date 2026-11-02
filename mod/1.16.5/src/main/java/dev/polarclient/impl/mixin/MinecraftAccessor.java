package dev.polarclient.impl.mixin;

import com.mojang.authlib.minecraft.SocialInteractionsService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the account switcher replace the signed-in session, and reads the FPS counter. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Mutable
	@Accessor("user")
	void polar$setUser(User user);

	@Mutable
	@Accessor("socialInteractionsService")
	void polar$setSocialInteractionsService(SocialInteractionsService service);

	@Accessor("fps")
	static int polar$fps() {
		throw new AssertionError();
	}
}
