package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.polarclient.impl.HealthTags;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Health Indicator on mobs' name tags. Players get theirs in {@code PlayerRendererMixin}, on the name only (their
 * scoreboard line comes through here too).
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@ModifyVariable(method = "renderNameTag", at = @At("HEAD"), argsOnly = true)
	private Component polar$health(Component name, Entity entity, Component original, PoseStack poseStack, MultiBufferSource buffers, int light) {
		return entity instanceof Player ? name : HealthTags.withHealth(name, entity);
	}
}
