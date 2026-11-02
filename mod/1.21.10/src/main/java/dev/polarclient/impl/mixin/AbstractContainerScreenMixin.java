package dev.polarclient.impl.mixin;

import dev.polarclient.hooks.InventoryHooks;
import dev.polarclient.impl.McGfx;
import dev.polarclient.impl.McPlatform;
import dev.polarclient.impl.ViewScreen;
import dev.polarclient.platform.ItemView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Inventory Search and Shulker Preview on every container screen (the creative inventory has its own search). */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> extends Screen {
	@Shadow protected int leftPos;
	@Shadow protected int topPos;
	@Shadow protected int imageWidth;
	@Shadow protected int imageHeight;
	@Shadow protected Slot hoveredSlot;
	@Shadow @Final protected T menu;

	protected AbstractContainerScreenMixin(Component title) {
		super(title);
	}

	@Unique
	private boolean polar$skip() {
		return (Object) this instanceof CreativeModeInventoryScreen;
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void polar$render(GuiGraphics graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (polar$skip()) return;
		List<Slot> slots = menu.slots;
		int[] xs = new int[slots.size()], ys = new int[slots.size()];
		ItemView[] items = new ItemView[slots.size()];
		for (int i = 0; i < slots.size(); i++) {
			Slot s = slots.get(i);
			xs[i] = leftPos + s.x;
			ys[i] = topPos + s.y;
			items[i] = s.hasItem() && s.isActive() ? McPlatform.view(s.getItem()) : null;
			if (!s.isActive()) xs[i] = -100;
		}
		ItemView hovered = hoveredSlot != null && hoveredSlot.hasItem() && menu.getCarried().isEmpty() ? McPlatform.view(hoveredSlot.getItem()) : null;
		InventoryHooks.render(McGfx.of(graphics), this, leftPos, topPos, imageWidth, imageHeight, xs, ys, items, hovered, mouseX, mouseY);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void polar$click(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (!polar$skip() && InventoryHooks.mouseClicked(event.x(), event.y())) cir.setReturnValue(true);
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void polar$key(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (!polar$skip() && InventoryHooks.keyPressed(ViewScreen.key(event))) cir.setReturnValue(true);
	}

	@Inject(method = "removed", at = @At("HEAD"))
	private void polar$closed(CallbackInfo ci) {
		InventoryHooks.closed();
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!polar$skip() && InventoryHooks.typing()) {
			for (char c : Character.toChars(event.codepoint())) InventoryHooks.charTyped(c);
			return true;
		}
		return super.charTyped(event);
	}
}
