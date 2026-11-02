package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.polarclient.hooks.InventoryHooks;
import dev.polarclient.impl.McGfx;
import dev.polarclient.impl.McPlatform;
import dev.polarclient.impl.ViewScreen;
import dev.polarclient.platform.ItemView;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
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
	private void polar$render(PoseStack pose, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
		if (polar$skip()) return;
		List<Slot> slots = menu.slots;
		int[] xs = new int[slots.size()], ys = new int[slots.size()];
		ItemView[] items = new ItemView[slots.size()];
		for (int i = 0; i < slots.size(); i++) {
			Slot s = slots.get(i);
			xs[i] = s.isActive() ? leftPos + s.x : -100;
			ys[i] = topPos + s.y;
			items[i] = s.hasItem() && s.isActive() ? McPlatform.view(s.getItem()) : null;
		}
		ItemView hovered = hoveredSlot != null && hoveredSlot.hasItem() && minecraft.player != null && minecraft.player.inventory.getCarried().isEmpty()
			? McPlatform.view(hoveredSlot.getItem()) : null;
		McGfx g = McGfx.of(pose);
		InventoryHooks.render(g, this, leftPos, topPos, imageWidth, imageHeight, xs, ys, items, hovered, mouseX, mouseY);
		g.end();
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void polar$click(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
		if (!polar$skip() && InventoryHooks.mouseClicked(x, y)) cir.setReturnValue(true);
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void polar$key(int key, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		if (!polar$skip() && InventoryHooks.keyPressed(ViewScreen.key(key, scanCode))) cir.setReturnValue(true);
	}

	@Inject(method = "removed", at = @At("HEAD"))
	private void polar$closed(CallbackInfo ci) {
		InventoryHooks.closed();
	}

	@Override
	public boolean charTyped(char c, int modifiers) {
		if (!polar$skip() && InventoryHooks.typing()) {
			InventoryHooks.charTyped(c);
			return true;
		}
		return super.charTyped(c, modifiers);
	}
}
