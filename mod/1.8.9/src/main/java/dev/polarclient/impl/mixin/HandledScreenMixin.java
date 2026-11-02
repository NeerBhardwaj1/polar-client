package dev.polarclient.impl.mixin;

import dev.polarclient.gui.KeyInput;
import dev.polarclient.hooks.InventoryHooks;
import dev.polarclient.impl.McGfx;
import dev.polarclient.impl.McPlatform;
import dev.polarclient.impl.ViewScreen;
import dev.polarclient.platform.ItemView;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.inventory.slot.Slot;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Inventory Search on every container screen (the creative inventory has its own search). */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {
	@Shadow protected int x;
	@Shadow protected int y;
	@Shadow protected int backgroundWidth;
	@Shadow protected int backgroundHeight;
	@Shadow private Slot focusedSlot;
	@Shadow public ScreenHandler screenHandler;

	@Unique
	private boolean polar$skip() {
		return (Object) this instanceof CreativeInventoryScreen;
	}

	/** After the slots, before the tooltip. */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;popMatrix()V", shift = At.Shift.AFTER))
	private void polar$render(int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (polar$skip()) return;
		List<Slot> slots = screenHandler.slots;
		int[] xs = new int[slots.size()], ys = new int[slots.size()];
		ItemView[] items = new ItemView[slots.size()];
		for (int i = 0; i < slots.size(); i++) {
			Slot s = slots.get(i);
			xs[i] = x + s.x;
			ys[i] = y + s.y;
			items[i] = s.hasStack() ? McPlatform.view(s.getStack()) : null;
		}
		McGfx g = McGfx.begin();
		InventoryHooks.render(g, this, x, y, backgroundWidth, backgroundHeight, xs, ys, items, null, mouseX, mouseY);
		g.end();
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void polar$click(int mouseX, int mouseY, int button, CallbackInfo ci) {
		if (!polar$skip() && InventoryHooks.mouseClicked(mouseX, mouseY)) ci.cancel();
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void polar$key(char c, int code, CallbackInfo ci) {
		if (polar$skip() || !InventoryHooks.typing()) return;
		ci.cancel();
		KeyInput k = ViewScreen.key(code);
		// 1.8.9 sends the key and the character it types together
		if (c >= ' ' && c != 127 && c != '§' && !k.control) InventoryHooks.charTyped(c);
		else InventoryHooks.keyPressed(k);
	}

	@Inject(method = "removed", at = @At("HEAD"))
	private void polar$closed(CallbackInfo ci) {
		InventoryHooks.closed();
	}
}
