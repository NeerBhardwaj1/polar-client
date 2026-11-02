package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import dev.polarclient.impl.McPlatform;
import dev.polarclient.modules.inventory.ItemDurabilityModule;
import dev.polarclient.platform.ItemView;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Exact Durability: one more tooltip line on tools and armour. */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@Inject(method = "getTooltipLines", at = @At("RETURN"))
	private void polar$durability(Player player, TooltipFlag flag, CallbackInfoReturnable<List<Component>> cir) {
		ItemStack self = (ItemStack) (Object) this;
		if (Polar.modules() == null || self.isEmpty()) return;
		ItemView view = McPlatform.view(self);
		String line = Polar.modules().itemDurability.line(view, flag.isAdvanced());
		if (line != null) cir.getReturnValue().add(new TextComponent(line).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(ItemDurabilityModule.color(view)))));
	}
}
