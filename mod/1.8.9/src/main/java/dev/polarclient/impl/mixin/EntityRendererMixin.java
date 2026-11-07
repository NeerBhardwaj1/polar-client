package dev.polarclient.impl.mixin;

import dev.polarclient.impl.HealthTags;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Health Indicator: health after the name in name tags. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@ModifyVariable(method = "renderLabelIfPresent", at = @At("HEAD"), argsOnly = true)
	private String polar$health(String text, Entity entity) {
		return HealthTags.withHealth(text, entity);
	}
}
