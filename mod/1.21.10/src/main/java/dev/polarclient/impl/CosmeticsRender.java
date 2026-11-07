package dev.polarclient.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.polarclient.cosmetics.RenderBatch;
import dev.polarclient.cosmetics.WingPose;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.UUID;

/** Draws Polar cosmetics (quads made by {@link dev.polarclient.cosmetics.Cosmetics}) and the Polar badge. */
public final class CosmeticsRender {
	private static final FontDescription BADGE_FONT = new FontDescription.Resource(ResourceLocation.fromNamespaceAndPath("polarclient", "badge"));
	private static final int FULL_BRIGHT = 0xF000F0;

	private CosmeticsRender() {
	}

	/** The player behind a render state's entity id, or null. */
	public static UUID uuid(int entityId) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;
		Entity e = mc.level.getEntity(entityId);
		return e instanceof Player ? e.getUUID() : null;
	}

	/** What the player behind a render state is doing, for their wings (see {@link WingPose}). */
	public static WingPose pose(int entityId, float moving) {
		WingPose p = WingPose.walking(moving);
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return p;
		Entity e = mc.level.getEntity(entityId);
		if (!(e instanceof Player)) return p;
		Player pl = (Player) e;
		p.sprinting = pl.isSprinting();
		p.sneaking = pl.isCrouching();
		p.onGround = pl.onGround();
		p.flying = pl.getAbilities().flying;
		p.gliding = pl.isFallFlying();
		p.swimming = pl.isSwimming();
		p.riding = pl.isPassenger();
		p.sleeping = pl.isSleeping();
		p.elytra = pl.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
		p.bodyYaw = pl.yBodyRot;
		Entity cam = mc.getCameraEntity();
		p.distanceSq = cam == null || cam == pl ? 0 : cam.distanceToSqr(pl);
		return p;
	}

	private static RenderType type(RenderBatch b) {
		ResourceLocation t = (ResourceLocation) b.texture;
		switch (b.layer) {
			case RenderBatch.CUTOUT:
				return RenderType.entityCutout(t);
			case RenderBatch.TRANSLUCENT:
				return RenderType.itemEntityTranslucentCull(t);
			case RenderBatch.GLOW:
				return RenderType.eyes(t);
			default:
				return RenderType.entityTranslucent(t);
		}
	}

	public static void submit(PoseStack poseStack, SubmitNodeCollector collector, RenderBatch b, int light) {
		if (!(b.texture instanceof ResourceLocation) || b.vertices() == 0) return;
		final int l = b.layer == RenderBatch.GLOW ? FULL_BRIGHT : light;
		collector.submitCustomGeometry(poseStack, type(b), (pose, vc) -> {
			for (int i = 0; i < b.vertices(); i++) {
				vc.addVertex(pose, b.x(i), b.y(i), b.z(i)).setColor(b.color(i)).setUv(b.u(i), b.v(i))
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(pose, b.nx(i), b.ny(i), b.nz(i));
			}
		});
	}

	/** The name with the Polar badge (a snowflake from the polarclient:badge font) in front. */
	public static Component badged(Component name) {
		Component badge = Component.literal("").withStyle(s -> s.withFont(BADGE_FONT).withColor(ChatFormatting.WHITE).withBold(false).withItalic(false));
		return Component.empty().append(badge).append(" ").append(name);
	}
}
