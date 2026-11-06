package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Motion Blur: the frame the effect keeps between frames, so a restarted blur can start from nothing. */
@Mixin(PostChain.class)
public interface PostChainAccessor {
	@Accessor("persistentTargets")
	Map<Identifier, RenderTarget> polar$persistentTargets();
}
