package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import dev.polarclient.impl.ChatText;
import dev.polarclient.impl.McPlatform;
import dev.polarclient.modules.inventory.ItemDurabilityModule;
import dev.polarclient.platform.ItemView;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Exact Durability: one more tooltip line on tools and armour. */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@Inject(method = "getTooltip", at = @At("RETURN"))
	private void polar$durability(PlayerEntity player, boolean advanced, CallbackInfoReturnable<List<String>> cir) {
		if (Polar.modules() == null) return;
		ItemView view = McPlatform.view((ItemStack) (Object) this);
		String line = Polar.modules().itemDurability.line(view, advanced);
		if (line != null) cir.getReturnValue().add(ChatText.nearest(ItemDurabilityModule.color(view)) + line);
	}
}
