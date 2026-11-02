package dev.polarclient.impl.mixin;

import dev.polarclient.util.CombatTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Your hits, for Reach Display and Combo Counter. */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
	@Inject(method = "attackEntity", at = @At("HEAD"))
	private void polar$hit(PlayerEntity player, Entity target, CallbackInfo ci) {
		if (player != MinecraftClient.getInstance().player || !(target instanceof LivingEntity)) return;
		Vec3d eye = player.getCameraPosVec(1.0f);
		Box b = target.getBoundingBox();
		double dx = Math.max(0, Math.max(b.minX - eye.x, eye.x - b.maxX));
		double dy = Math.max(0, Math.max(b.minY - eye.y, eye.y - b.maxY));
		double dz = Math.max(0, Math.max(b.minZ - eye.z, eye.z - b.maxZ));
		CombatTracker.onHit(Math.sqrt(dx * dx + dy * dy + dz * dz));
	}
}
