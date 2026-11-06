package dev.polarclient.impl.mixin;

import net.minecraft.client.gl.PostProcessShader;
import net.minecraft.client.gl.ShaderEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Motion Blur: the passes of its shader effect, to set the blur strength on the first one. */
@Mixin(ShaderEffect.class)
public interface ShaderEffectAccessor {
	@Accessor("passes")
	List<PostProcessShader> polar$passes();
}
