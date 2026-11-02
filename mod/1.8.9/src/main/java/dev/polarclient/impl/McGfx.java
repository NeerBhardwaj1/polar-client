package dev.polarclient.impl;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.polarclient.gfx.Gfx;
import dev.polarclient.gfx.UiFont;
import dev.polarclient.platform.EffectView;
import dev.polarclient.platform.ItemView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.Window;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Polar's drawing calls on 1.8.9's fixed-function OpenGL. Transforms go on the GL matrix (and are mirrored here to
 * place scissor rectangles); flat and gradient rectangles are batched into one draw. Call {@link #end()} when a
 * frame's drawing is done.
 */
public final class McGfx implements Gfx {
	private static final McGfx INSTANCE = new McGfx();
	private static final int GL_FLAT = 7424, GL_SMOOTH = 7425, GL_GREATER = 516;
	private static final Identifier INVENTORY = new Identifier("textures/gui/container/inventory.png");
	private static final Map<String, Identifier> TEXTURES = new HashMap<String, Identifier>();
	private static final Map<UUID, Identifier> SKINS = Collections.synchronizedMap(new HashMap<UUID, Identifier>());
	private static final Set<UUID> SKIN_REQUESTS = new HashSet<UUID>();

	private final BufferBuilder fills = new BufferBuilder(1 << 16);
	private final BufferRenderer renderer = new BufferRenderer();
	private boolean batching;
	/** Mirror of the GL transform: {scaleX, scaleY, translateX, translateY}. */
	private float[] affine = {1, 1, 0, 0};
	private final Deque<float[]> affineStack = new ArrayDeque<float[]>();
	/** Scissor rectangles in window pixels (x, y from the bottom, w, h); the top one is active. */
	private final Deque<int[]> scissors = new ArrayDeque<int[]>();
	private Window window;

	private McGfx() {
	}

	/** Starts a frame's drawing in GUI coordinates. */
	public static McGfx begin() {
		INSTANCE.flush();
		INSTANCE.affine = new float[] {1, 1, 0, 0};
		INSTANCE.affineStack.clear();
		INSTANCE.window = new Window(MinecraftClient.getInstance());
		return INSTANCE;
	}

	private static MinecraftClient mc() {
		return MinecraftClient.getInstance();
	}

	static Identifier texture(String path) {
		Identifier id = TEXTURES.get(path);
		if (id == null) TEXTURES.put(path, id = new Identifier("polarclient", path));
		return id;
	}

	/** Draws whatever is still batched and leaves no scissor behind. */
	public void end() {
		flush();
		if (!scissors.isEmpty()) {
			scissors.clear();
			GL11.glDisable(GL11.GL_SCISSOR_TEST);
		}
		GlStateManager.color(1, 1, 1, 1);
	}

	// ------------------------------------------------------------------ batching

	private void beginFills() {
		if (batching) return;
		fills.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
		batching = true;
	}

	private void flush() {
		if (!batching) return;
		batching = false;
		fills.end();
		GlStateManager.enableBlend();
		GlStateManager.blendFuncSeparate(770, 771, 1, 0);
		GlStateManager.disableTexture();
		GlStateManager.alphaFunc(GL_GREATER, 0.001f);
		GlStateManager.shadeModel(GL_SMOOTH);
		GlStateManager.color(1, 1, 1, 1);
		renderer.draw(fills);
		GlStateManager.shadeModel(GL_FLAT);
		GlStateManager.alphaFunc(GL_GREATER, 0.1f);
		GlStateManager.enableTexture();
	}

	private void vertex(float x, float y, int argb) {
		fills.vertex(x, y, 0).color(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24).next();
	}

	// ------------------------------------------------------------------ Gfx

	@Override
	public int width() {
		return window.getWidth();
	}

	@Override
	public int height() {
		return window.getHeight();
	}

	@Override
	public int guiScale() {
		return window.getScaleFactor();
	}

	@Override
	public void push() {
		flush();
		GlStateManager.pushMatrix();
		affineStack.push(affine.clone());
	}

	@Override
	public void pop() {
		flush();
		GlStateManager.popMatrix();
		if (!affineStack.isEmpty()) affine = affineStack.pop();
	}

	@Override
	public void translate(float x, float y) {
		flush();
		GlStateManager.translate(x, y, 0);
		affine[2] += affine[0] * x;
		affine[3] += affine[1] * y;
	}

	@Override
	public void scale(float sx, float sy) {
		flush();
		GlStateManager.scale(sx, sy, 1);
		affine[0] *= sx;
		affine[1] *= sy;
	}

	/** Scissors set up inside a rotation aren't supported (the scissor maths only follows translate and scale). */
	@Override
	public void rotate(float degrees) {
		flush();
		GlStateManager.rotate(degrees, 0, 0, 1);
	}

	@Override
	public void fill(int x0, int y0, int x1, int y1, int argb) {
		if ((argb >>> 24) == 0) return;
		gradient(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1), argb, argb);
	}

	@Override
	public void gradient(int x0, int y0, int x1, int y1, int top, int bottom) {
		beginFills();
		vertex(x0, y1, bottom);
		vertex(x1, y1, bottom);
		vertex(x1, y0, top);
		vertex(x0, y0, top);
	}

	@Override
	public void blit(String texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argb) {
		blitId(texture(texture), x, y, w, h, u0, v0, u1, v1, argb);
	}

	private void blitId(Identifier id, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argb) {
		flush();
		mc().getTextureManager().bindTexture(id);
		texturedQuad(x, y, w, h, u0, v0, u1, v1, argb);
	}

	private void texturedQuad(float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argb) {
		int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
		GlStateManager.enableTexture();
		GlStateManager.enableBlend();
		GlStateManager.blendFuncSeparate(770, 771, 1, 0);
		GlStateManager.alphaFunc(GL_GREATER, 0.001f);
		GlStateManager.color(1, 1, 1, 1);
		Tessellator t = Tessellator.getInstance();
		BufferBuilder buf = t.getBuffer();
		buf.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
		buf.vertex(x, y + h, 0).texture(u0, v1).color(r, g, b, a).next();
		buf.vertex(x + w, y + h, 0).texture(u1, v1).color(r, g, b, a).next();
		buf.vertex(x + w, y, 0).texture(u1, v0).color(r, g, b, a).next();
		buf.vertex(x, y, 0).texture(u0, v0).color(r, g, b, a).next();
		t.draw();
		GlStateManager.alphaFunc(GL_GREATER, 0.1f);
	}

	@Override
	public void blitHandle(Object texture, float x, float y, float w, float h) {
		if (texture instanceof Identifier) blitId((Identifier) texture, x, y, w, h, 0, 0, 1, 1, 0xFFFFFFFF);
	}

	@Override
	public void text(String s, float x, float y, int argb, UiFont font, boolean shadow) {
		// the vanilla renderer treats alpha below 4 as opaque, so skip what should be invisible
		if ((argb >>> 24) < 4 || s.isEmpty()) return;
		flush();
		if (font == UiFont.VANILLA) {
			GlStateManager.enableBlend();
			GlStateManager.blendFuncSeparate(770, 771, 1, 0);
			mc().textRenderer.draw(s, x, y, argb, shadow);
		} else {
			TtfFont.of(font).draw(s, x, y, argb, shadow);
		}
		GlStateManager.color(1, 1, 1, 1);
	}

	/** On 1.8.9 a server MOTD is a string with formatting codes. */
	@Override
	public void richText(Object component, float x, float y, int maxWidth, int maxLines, int lineHeight, int argb) {
		if (!(component instanceof String) || (argb >>> 24) < 4) return;
		flush();
		GlStateManager.enableBlend();
		List<String> lines = mc().textRenderer.wrapLines((String) component, maxWidth);
		for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
			mc().textRenderer.draw(lines.get(i), Math.round(x), Math.round(y + i * lineHeight), argb, false);
		}
		GlStateManager.color(1, 1, 1, 1);
	}

	@Override
	public void richLine(Object component, float x, float y, int argb, boolean shadow) {
		if (component == null || (argb >>> 24) < 4) return;
		String s = component instanceof net.minecraft.text.Text ? ((net.minecraft.text.Text) component).asFormattedString() : component.toString();
		flush();
		GlStateManager.enableBlend();
		mc().textRenderer.draw(s, x, y, argb, shadow);
		GlStateManager.color(1, 1, 1, 1);
	}

	@Override
	public void scissor(int x0, int y0, int x1, int y1) {
		flush();
		float s = window.getScaleFactor();
		float ax = affine[2] + affine[0] * x0, bx = affine[2] + affine[0] * x1;
		float ay = affine[3] + affine[1] * y0, by = affine[3] + affine[1] * y1;
		int left = (int) Math.floor(Math.min(ax, bx) * s), right = (int) Math.ceil(Math.max(ax, bx) * s);
		int top = (int) Math.floor(Math.min(ay, by) * s), bottom = (int) Math.ceil(Math.max(ay, by) * s);
		int[] r = {left, mc().height - bottom, Math.max(0, right - left), Math.max(0, bottom - top)};
		int[] outer = scissors.peek();
		if (outer != null) {
			int rx = Math.max(r[0], outer[0]), ry = Math.max(r[1], outer[1]);
			int rr = Math.min(r[0] + r[2], outer[0] + outer[2]), rt = Math.min(r[1] + r[3], outer[1] + outer[3]);
			r = new int[] {rx, ry, Math.max(0, rr - rx), Math.max(0, rt - ry)};
		}
		scissors.push(r);
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor(r[0], r[1], r[2], r[3]);
	}

	@Override
	public void endScissor() {
		flush();
		if (!scissors.isEmpty()) scissors.pop();
		int[] r = scissors.peek();
		if (r == null) GL11.glDisable(GL11.GL_SCISSOR_TEST);
		else GL11.glScissor(r[0], r[1], r[2], r[3]);
	}

	@Override
	public void nextLayer() {
		// items draw at a raised depth; clearing depth lets later drawing cover them, like a new layer
		flush();
		GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
	}

	private void items(ItemView item, int x, int y, boolean decorations) {
		if (item == null || !(item.handle instanceof ItemStack)) return;
		flush();
		ItemStack stack = (ItemStack) item.handle;
		// the same setup the hotbar uses
		GlStateManager.enableRescaleNormal();
		DiffuseLighting.enable();
		if (decorations) mc().getItemRenderer().renderGuiItemOverlay(mc().textRenderer, stack, x, y);
		else mc().getItemRenderer().renderInGuiWithOverrides(stack, x, y);
		DiffuseLighting.disable();
		GlStateManager.disableRescaleNormal();
		GlStateManager.disableLighting();
		GlStateManager.enableBlend();
		GlStateManager.color(1, 1, 1, 1);
	}

	@Override
	public void item(ItemView item, int x, int y) {
		items(item, x, y, false);
	}

	@Override
	public void itemDecorations(ItemView item, int x, int y) {
		items(item, x, y, true);
	}

	@Override
	public void effectIcon(EffectView effect, int x, int y, int size, float alpha) {
		if (!(effect.handle instanceof StatusEffectInstance)) return;
		StatusEffect type = StatusEffect.STATUS_EFFECTS[((StatusEffectInstance) effect.handle).getEffectId()];
		if (type == null || !type.hasIcon()) return;
		int index = type.getIconLevel();
		float u = index % 8 * 18 / 256f, v = (198 + index / 8 * 18) / 256f;
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
		blitId(INVENTORY, x, y, size, size, u, v, u + 18 / 256f, v + 18 / 256f, a << 24 | 0xFFFFFF);
	}

	@Override
	public void playerHead(UUID id, String name, float x, float y, float size) {
		Identifier skin = skin(id, name);
		blitId(skin, x, y, size, size, 8 / 64f, 8 / 64f, 16 / 64f, 16 / 64f, 0xFFFFFFFF);
		blitId(skin, x, y, size, size, 40 / 64f, 8 / 64f, 48 / 64f, 16 / 64f, 0xFFFFFFFF);
	}

	/** The player's skin: from the server you're on, else fetched once from Mojang (the default skin until then). */
	private static Identifier skin(final UUID id, final String name) {
		final MinecraftClient mc = mc();
		if (mc.getNetworkHandler() != null) {
			PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(id);
			if (entry != null) return entry.getSkinTexture();
		}
		Identifier known = SKINS.get(id);
		if (known != null) return known;
		if (SKIN_REQUESTS.add(id)) {
			CompletableFuture.supplyAsync(() -> mc.getSessionService().fillProfileProperties(new GameProfile(id, name), false))
				.thenAccept(profile -> mc.submit(() -> mc.getSkinProvider().loadProfileSkin(profile, (type, location, texture) -> {
					if (type == MinecraftProfileTexture.Type.SKIN) SKINS.put(id, location);
				}, false)));
		}
		return DefaultSkinHelper.getTexture(id);
	}

	@Override
	public void blurBackground() {
		// 1.8.9 has no menu blur; Polar's menus darken the background instead
	}
}
