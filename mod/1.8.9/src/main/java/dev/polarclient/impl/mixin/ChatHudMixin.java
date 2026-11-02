package dev.polarclient.impl.mixin;

import dev.polarclient.Polar;
import dev.polarclient.hooks.ChatHooks;
import dev.polarclient.impl.ChatText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Chat lines for AutoGG and the chat mods (time stamps, click-to-copy, filter, stacking, background colour). */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin extends DrawableHelper {
	@Shadow @Final private List<ChatHudLine> messages;

	@Unique private static boolean polar$busy;
	@Unique private ChatHudLine polar$last;

	@Shadow
	public abstract void addMessage(Text message, int id);

	@Shadow
	public abstract void reset();

	@Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
	private void polar$chat(Text message, CallbackInfo ci) {
		if (Polar.modules() == null || message == null) return;
		// 1.8.9 can't tell server messages from player chat here, so both kinds of mods see every line
		String text = message.asUnformattedString();
		Polar.onGameMessage(text, false);
		Polar.onChatMessage(text);
	}

	@Inject(method = "addMessage(Lnet/minecraft/text/Text;I)V", at = @At("HEAD"), cancellable = true)
	private void polar$addMessage(Text message, int id, CallbackInfo ci) {
		if (polar$busy || message == null) return;
		ChatHooks.Line line = ChatHooks.incoming(message.asUnformattedString());
		if (line == null) return;
		ci.cancel();
		if (line.hide) return;
		if (line.repeat > 1) {
			if (!messages.isEmpty() && messages.get(0) == polar$last) {
				messages.remove(0);
				reset();
			} else {
				ChatHooks.repeatLost();
				line.repeat = 1;
			}
		}
		polar$busy = true;
		try {
			addMessage(ChatText.decorate(message, line), id);
		} finally {
			polar$busy = false;
		}
		polar$last = messages.isEmpty() ? null : messages.get(0);
	}

	/** The per-line background (the first box chat draws for each line). */
	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHud;fill(IIIII)V", ordinal = 0))
	private void polar$background(int x0, int y0, int x1, int y1, int color) {
		if (ChatHooks.customBackground()) {
			float full = 255f * (MinecraftClient.getInstance().options.chatOpacity * 0.9f + 0.1f) / 2f;
			color = ChatHooks.background(color, full);
		}
		fill(x0, y0, x1, y1, color);
	}
}
