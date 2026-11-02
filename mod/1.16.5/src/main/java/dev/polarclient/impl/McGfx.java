package dev.polarclient.impl;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import dev.polarclient.gfx.Gfx;
import dev.polarclient.gfx.UiFont;
import dev.polarclient.platform.EffectView;
import dev.polarclient.platform.ItemView;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Polar's drawing calls on 1.16.5's immediate-mode renderer. Flat and gradient rectangles are collected into one
 * vertex buffer and drawn together (the menus draw thousands of them); anything else flushes that batch first.
 * Call {@link #end()} when a frame's drawing is done.
 */
public final class McGfx implements Gfx {
	private static final McGfx INSTANCE = new McGfx();
	private static final int GL_QUADS = 7, GL_FLAT = 7424, GL_SMOOTH = 7425, GL_GREATER = 516, GL_DEPTH_BUFFER_BIT = 256;
	private static final Map<String, ResourceLocation> TEXTURES = new HashMap<String, ResourceLocation>();
	private static final Map<UiFont, ResourceLocation> FONTS = new EnumMap<UiFont, ResourceLocation>(UiFont.class);
	private static final Map<UUID, ResourceLocation> SKINS = Collections.synchronizedMap(new HashMap<UUID, ResourceLocation>());
	private static final Set<UUID> SKIN_REQUESTS = new HashSet<UUID>();

	private final BufferBuilder fills = new BufferBuilder(1 << 16);
	private boolean batching;
	private PoseStack pose;
	/** Scissor rectangles in window pixels (x, y from the bottom, w, h); the top one is active. */
	private final Deque<int[]> scissors = new ArrayDeque<int[]>();

	private McGfx() {
	}

	public static McGfx of(PoseStack pose) {
		INSTANCE.flush();
		INSTANCE.pose = pose;
		return INSTANCE;
	}

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	static ResourceLocation texture(String path) {
		ResourceLocation id = TEXTURES.get(path);
		if (id == null) TEXTURES.put(path, id = new ResourceLocation("polarclient", path));
		return id;
	}

	static Component styled(String s, UiFont font) {
		if (font == UiFont.VANILLA) return new TextComponent(s);
		ResourceLocation id = FONTS.get(font);
		if (id == null) FONTS.put(font, id = new ResourceLocation("polarclient", font.id()));
		final ResourceLocation fontId = id;
		return new TextComponent(s).withStyle(style -> style.withFont(fontId));
	}

	/** Draws whatever is still batched and leaves no scissor behind. */
	public void end() {
		flush();
		if (!scissors.isEmpty()) {
			scissors.clear();
			RenderSystem.disableScissor();
		}
	}

	// ------------------------------------------------------------------ batching

	private Matrix4f matrix() {
		return pose.last().pose();
	}

	private void beginFills() {
		if (batching) return;
		fills.begin(GL_QUADS, DefaultVertexFormat.POSITION_COLOR);
		batching = true;
	}

	private void flush() {
		if (!batching) return;
		batching = false;
		fills.end();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableTexture();
		RenderSystem.alphaFunc(GL_GREATER, 0.001f);
		RenderSystem.shadeModel(GL_SMOOTH);
		BufferUploader.end(fills);
		RenderSystem.shadeModel(GL_FLAT);
		RenderSystem.defaultAlphaFunc();
		RenderSystem.enableTexture();
	}

	private void quad(float x0, float y0, float x1, float y1, int top, int bottom) {
		Matrix4f m = matrix();
		vertex(m, x0, y1, bottom);
		vertex(m, x1, y1, bottom);
		vertex(m, x1, y0, top);
		vertex(m, x0, y0, top);
	}

	private void vertex(Matrix4f m, float x, float y, int argb) {
		fills.vertex(m, x, y, 0).color(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24).endVertex();
	}

	// ------------------------------------------------------------------ Gfx

	@Override
	public int width() {
		return mc().getWindow().getGuiScaledWidth();
	}

	@Override
	public int height() {
		return mc().getWindow().getGuiScaledHeight();
	}

	@Override
	public int guiScale() {
		return (int) mc().getWindow().getGuiScale();
	}

	@Override
	public void push() {
		pose.pushPose();
	}

	@Override
	public void pop() {
		pose.popPose();
	}

	@Override
	public void translate(float x, float y) {
		pose.translate(x, y, 0);
	}

	@Override
	public void scale(float sx, float sy) {
		pose.scale(sx, sy, 1);
	}

	@Override
	public void rotate(float degrees) {
		pose.mulPose(Vector3f.ZP.rotationDegrees(degrees));
	}

	@Override
	public void fill(int x0, int y0, int x1, int y1, int argb) {
		if ((argb >>> 24) == 0) return;
		if (x0 > x1) {
			int t = x0;
			x0 = x1;
			x1 = t;
		}
		if (y0 > y1) {
			int t = y0;
			y0 = y1;
			y1 = t;
		}
		beginFills();
		quad(x0, y0, x1, y1, argb, argb);
	}

	@Override
	public void gradient(int x0, int y0, int x1, int y1, int top, int bottom) {
		beginFills();
		quad(x0, y0, x1, y1, top, bottom);
	}

	@Override
	public void blit(String texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argb) {
		blitId(texture(texture), x, y, w, h, u0, v0, u1, v1, argb);
	}

	private void blitId(ResourceLocation id, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argb) {
		flush();
		mc().getTextureManager().bind(id);
		texturedQuad(x, y, w, h, u0, v0, u1, v1, argb);
	}

	private void texturedQuad(float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argb) {
		int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
		Matrix4f m = matrix();
		BufferBuilder buf = fills;
		buf.begin(GL_QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
		buf.vertex(m, x, y + h, 0).color(r, g, b, a).uv(u0, v1).endVertex();
		buf.vertex(m, x + w, y + h, 0).color(r, g, b, a).uv(u1, v1).endVertex();
		buf.vertex(m, x + w, y, 0).color(r, g, b, a).uv(u1, v0).endVertex();
		buf.vertex(m, x, y, 0).color(r, g, b, a).uv(u0, v0).endVertex();
		buf.end();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.alphaFunc(GL_GREATER, 0.001f);
		RenderSystem.color4f(1, 1, 1, 1);
		BufferUploader.end(buf);
		RenderSystem.defaultAlphaFunc();
	}

	@Override
	public void blitHandle(Object texture, float x, float y, float w, float h) {
		if (texture instanceof ResourceLocation) blitId((ResourceLocation) texture, x, y, w, h, 0, 0, 1, 1, 0xFFFFFFFF);
	}

	@Override
	public void text(String s, float x, float y, int argb, UiFont font, boolean shadow) {
		// the font treats alpha below 4 as opaque, so skip what should be invisible
		if ((argb >>> 24) < 4 || s.isEmpty()) return;
		flush();
		Component c = styled(s, font);
		if (shadow) mc().font.drawShadow(pose, c, x, y, argb);
		else mc().font.draw(pose, c, x, y, argb);
	}

	@Override
	public void richText(Object component, float x, float y, int maxWidth, int maxLines, int lineHeight, int argb) {
		if (!(component instanceof FormattedText) || (argb >>> 24) < 4) return;
		flush();
		List<FormattedCharSequence> lines = mc().font.split((FormattedText) component, maxWidth);
		for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
			mc().font.draw(pose, lines.get(i), Math.round(x), Math.round(y + i * lineHeight), argb);
		}
	}

	@Override
	public void richLine(Object component, float x, float y, int argb, boolean shadow) {
		if (component == null || (argb >>> 24) < 4) return;
		flush();
		if (component instanceof String) {
			if (shadow) mc().font.drawShadow(pose, (String) component, x, y, argb);
			else mc().font.draw(pose, (String) component, x, y, argb);
		} else if (component instanceof Component) {
			if (shadow) mc().font.drawShadow(pose, (Component) component, x, y, argb);
			else mc().font.draw(pose, (Component) component, x, y, argb);
		}
	}

	@Override
	public void scissor(int x0, int y0, int x1, int y1) {
		flush();
		Matrix4f m = matrix();
		Vector4f a = new Vector4f(x0, y0, 0, 1), b = new Vector4f(x1, y1, 0, 1);
		a.transform(m);
		b.transform(m);
		double s = mc().getWindow().getGuiScale();
		int winH = mc().getWindow().getHeight();
		int left = (int) Math.floor(Math.min(a.x(), b.x()) * s), right = (int) Math.ceil(Math.max(a.x(), b.x()) * s);
		int top = (int) Math.floor(Math.min(a.y(), b.y()) * s), bottom = (int) Math.ceil(Math.max(a.y(), b.y()) * s);
		int[] r = {left, winH - bottom, Math.max(0, right - left), Math.max(0, bottom - top)};
		int[] outer = scissors.peek();
		if (outer != null) {
			int rx = Math.max(r[0], outer[0]), ry = Math.max(r[1], outer[1]);
			int rr = Math.min(r[0] + r[2], outer[0] + outer[2]), rt = Math.min(r[1] + r[3], outer[1] + outer[3]);
			r = new int[] {rx, ry, Math.max(0, rr - rx), Math.max(0, rt - ry)};
		}
		scissors.push(r);
		RenderSystem.enableScissor(r[0], r[1], r[2], r[3]);
	}

	@Override
	public void endScissor() {
		flush();
		if (!scissors.isEmpty()) scissors.pop();
		int[] r = scissors.peek();
		if (r == null) RenderSystem.disableScissor();
		else RenderSystem.enableScissor(r[0], r[1], r[2], r[3]);
	}

	@Override
	public void nextLayer() {
		// items draw at a raised depth; clearing depth lets later drawing cover them, like a new layer
		flush();
		RenderSystem.clear(GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
	}

	@Override
	public void item(ItemView item, int x, int y) {
		if (item == null || !(item.handle instanceof ItemStack)) return;
		flush();
		RenderSystem.pushMatrix();
		RenderSystem.multMatrix(matrix());
		mc().getItemRenderer().renderGuiItem((ItemStack) item.handle, x, y);
		RenderSystem.popMatrix();
	}

	@Override
	public void itemDecorations(ItemView item, int x, int y) {
		if (item == null || !(item.handle instanceof ItemStack)) return;
		flush();
		RenderSystem.pushMatrix();
		RenderSystem.multMatrix(matrix());
		mc().getItemRenderer().renderGuiItemDecorations(mc().font, (ItemStack) item.handle, x, y);
		RenderSystem.popMatrix();
	}

	@Override
	public void effectIcon(EffectView effect, int x, int y, int size, float alpha) {
		if (!(effect.handle instanceof MobEffectInstance)) return;
		flush();
		TextureAtlasSprite sprite = mc().getMobEffectTextures().get(((MobEffectInstance) effect.handle).getEffect());
		mc().getTextureManager().bind(sprite.atlas().location());
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
		texturedQuad(x, y, size, size, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), a << 24 | 0xFFFFFF);
	}

	@Override
	public void playerHead(UUID id, String name, float x, float y, float size) {
		ResourceLocation skin = skin(id, name);
		flush();
		mc().getTextureManager().bind(skin);
		texturedQuad(x, y, size, size, 8 / 64f, 8 / 64f, 16 / 64f, 16 / 64f, 0xFFFFFFFF);
		texturedQuad(x, y, size, size, 40 / 64f, 8 / 64f, 48 / 64f, 16 / 64f, 0xFFFFFFFF);
	}

	/** The player's skin: from the server you're on, else fetched once from Mojang (the default skin until then). */
	private static ResourceLocation skin(UUID id, String name) {
		Minecraft mc = mc();
		if (mc.getConnection() != null) {
			PlayerInfo info = mc.getConnection().getPlayerInfo(id);
			if (info != null) return info.getSkinLocation();
		}
		ResourceLocation known = SKINS.get(id);
		if (known != null) return known;
		if (SKIN_REQUESTS.add(id)) {
			CompletableFuture.supplyAsync(() -> mc.getMinecraftSessionService().fillProfileProperties(new GameProfile(id, name), false), Util.backgroundExecutor())
				.thenAcceptAsync(profile -> mc.getSkinManager().registerSkins(profile, (type, location, texture) -> {
					if (type == MinecraftProfileTexture.Type.SKIN) SKINS.put(id, location);
				}, false), mc);
		}
		return DefaultPlayerSkin.getDefaultSkin(id);
	}

	@Override
	public void blurBackground() {
		// 1.16.5 has no menu blur; Polar's menus darken the background instead
	}
}
