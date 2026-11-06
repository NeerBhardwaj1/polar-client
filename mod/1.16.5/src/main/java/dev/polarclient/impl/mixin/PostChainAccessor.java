package dev.polarclient.impl.mixin;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Motion Blur: the passes of its post chain, to set the blur strength on the first one. */
@Mixin(PostChain.class)
public interface PostChainAccessor {
	@Accessor("passes")
	List<PostPass> polar$passes();
}
