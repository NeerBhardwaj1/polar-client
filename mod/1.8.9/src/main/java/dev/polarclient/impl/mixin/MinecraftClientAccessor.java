package dev.polarclient.impl.mixin;

import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the account switcher replace the signed-in session and drop the old account's profile properties. */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
	@Mutable
	@Accessor("session")
	void polar$setSession(Session session);

	@Accessor("sessionPropertyMap")
	PropertyMap polar$sessionProperties();
}
