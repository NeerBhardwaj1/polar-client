package dev.polarclient.impl.mixin;

import dev.polarclient.hooks.ChatHooks;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Copy Chat: a click on a marked chat line copies its text. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Inject(method = "handleTextClick", at = @At("HEAD"), cancellable = true)
	private void polar$copy(Text text, CallbackInfoReturnable<Boolean> cir) {
		if (text == null) return;
		ClickEvent click = text.getStyle().getClickEvent();
		if (click != null && click.getAction() == ClickEvent.Action.SUGGEST_COMMAND && click.getValue() != null
			&& click.getValue().startsWith(ChatHooks.COPY_PREFIX)) {
			Screen.setClipboard(click.getValue().substring(ChatHooks.COPY_PREFIX.length()));
			cir.setReturnValue(true);
		}
	}
}
