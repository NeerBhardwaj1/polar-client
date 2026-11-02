package dev.polarclient.impl;

import dev.polarclient.gfx.Gfx;
import dev.polarclient.gfx.UiFont;
import dev.polarclient.platform.EffectView;
import dev.polarclient.platform.ItemView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Polar's drawing calls on top of 1.21.11's GuiGraphics. One instance, re-pointed at each frame's graphics. */
public final class McGfx implements Gfx {
	private static final McGfx INSTANCE = new McGfx();
	/** Virtual texture size for sub-pixel UVs: blit() takes texel offsets, so 0..1 UVs are scaled up to this. */
	private static final int UV = 1 << 14;
	private static final Map<String, Identifier> TEXTURES = new HashMap<>();
	private static final Map<UiFont, FontDescription> FONTS = new EnumMap<>(UiFont.class);
	private static final Map<UUID, Component> HEADS = new HashMap<>();

	private GuiGraphics g;

	private McGfx() {
	}

	public static McGfx of(GuiGraphics graphics) {
		INSTANCE.g = graphics;
		return INSTANCE;
	}

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	static Identifier texture(String path) {
		return TEXTURES.computeIfAbsent(path, p -> Identifier.fromNamespaceAndPath("polarclient", p));
	}

	static Component styled(String s, UiFont font) {
		if (font == UiFont.VANILLA) return Component.literal(s);
		FontDescription d = FONTS.computeIfAbsent(font, f -> new FontDescription.Resource(Identifier.fromNamespaceAndPath("polarclient", f.id())));
		return Component.literal(s).withStyle(style -> style.withFont(d));
	}

	@Override
	public int width() {
		return g.guiWidth();
	}

	@Override
	public int height() {
		return g.guiHeight();
	}

	@Override
	public int guiScale() {
		return mc().getWindow().getGuiScale();
	}

	@Override
	public void push() {
		g.pose().pushMatrix();
	}

	@Override
	public void pop() {
		g.pose().popMatrix();
	}

	@Override
	public void translate(float x, float y) {
		g.pose().translate(x, y);
	}

	@Override
	public void scale(float sx, float sy) {
		g.pose().scale(sx, sy);
	}

	@Override
	public void rotate(float degrees) {
		g.pose().rotate((float) Math.toRadians(degrees));
	}

	@Override
	public void fill(int x0, int y0, int x1, int y1, int argb) {
		g.fill(x0, y0, x1, y1, argb);
	}

	@Override
	public void gradient(int x0, int y0, int x1, int y1, int top, int bottom) {
		g.fillGradient(x0, y0, x1, y1, top, bottom);
	}

	@Override
	public void blit(String texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argb) {
		blitId(texture(texture), x, y, w, h, u0, v0, u1, v1, argb);
	}

	private void blitId(Identifier id, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argb) {
		int srcW = Math.round((u1 - u0) * UV), srcH = Math.round((v1 - v0) * UV);
		int ix = (int) x, iy = (int) y, iw = (int) w, ih = (int) h;
		if (ix == x && iy == y && iw == w && ih == h) {
			g.blit(RenderPipelines.GUI_TEXTURED, id, ix, iy, u0 * UV, v0 * UV, iw, ih, srcW, srcH, UV, UV, argb);
			return;
		}
		// fractional box: draw a unit quad stretched by the pose
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(w, h);
		g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, u0 * UV, v0 * UV, 1, 1, srcW, srcH, UV, UV, argb);
		g.pose().popMatrix();
	}

	@Override
	public void blitHandle(Object texture, float x, float y, float w, float h) {
		if (texture instanceof Identifier id) blitId(id, x, y, w, h, 0, 0, 1, 1, 0xFFFFFFFF);
	}

	@Override
	public void text(String s, float x, float y, int argb, UiFont font, boolean shadow) {
		Component c = styled(s, font);
		int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
		float fx = x - ix, fy = y - iy;
		if (fx == 0 && fy == 0) {
			g.drawString(mc().font, c, ix, iy, argb, shadow);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(fx, fy);
		g.drawString(mc().font, c, ix, iy, argb, shadow);
		g.pose().popMatrix();
	}

	@Override
	public void richText(Object component, float x, float y, int maxWidth, int maxLines, int lineHeight, int argb) {
		if (!(component instanceof FormattedText text)) return;
		List<FormattedCharSequence> lines = mc().font.split(text, maxWidth);
		for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
			g.drawString(mc().font, lines.get(i), Math.round(x), Math.round(y + i * lineHeight), argb, false);
		}
	}

	@Override
	public void richLine(Object component, float x, float y, int argb, boolean shadow) {
		if (component == null) return;
		g.pose().pushMatrix();
		g.pose().translate(x - (int) Math.floor(x), y - (int) Math.floor(y));
		if (component instanceof String str) g.drawString(mc().font, str, (int) Math.floor(x), (int) Math.floor(y), argb, shadow);
		else if (component instanceof Component c) g.drawString(mc().font, c, (int) Math.floor(x), (int) Math.floor(y), argb, shadow);
		g.pose().popMatrix();
	}

	@Override
	public void scissor(int x0, int y0, int x1, int y1) {
		g.enableScissor(x0, y0, x1, y1);
	}

	@Override
	public void endScissor() {
		g.disableScissor();
	}

	@Override
	public void nextLayer() {
		g.nextStratum();
	}

	@Override
	public void item(ItemView item, int x, int y) {
		if (item != null && item.handle instanceof ItemStack stack) g.renderItem(stack, x, y);
	}

	@Override
	public void itemDecorations(ItemView item, int x, int y) {
		if (item != null && item.handle instanceof ItemStack stack) g.renderItemDecorations(mc().font, stack, x, y);
	}

	@Override
	public void effectIcon(EffectView effect, int x, int y, int size, float alpha) {
		if (effect.handle instanceof MobEffectInstance e) {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(e.getEffect()), x, y, size, size, alpha);
		}
	}

	@Override
	public void playerHead(UUID id, String name, float x, float y, float size) {
		if (HEADS.size() > 64) HEADS.clear();
		Component head = HEADS.computeIfAbsent(id, k -> Component.literal(" ")
			.withStyle(s -> s.withFont(new FontDescription.PlayerSprite(ResolvableProfile.createUnresolved(k), true))));
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(size / 8f);
		g.drawString(mc().font, head, 0, 0, 0xFFFFFFFF, false);
		g.pose().popMatrix();
	}

	@Override
	public void blurBackground() {
		if (mc().options.getMenuBackgroundBlurriness() >= 1) g.blurBeforeThisStratum();
	}
}
