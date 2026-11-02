package dev.polarclient.impl.mixin;

import dev.polarclient.hooks.ChatHooks;
import dev.polarclient.impl.ChatText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Chat mods: time stamps, click-to-copy, filter, repeated-line stacking and the background colour. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@Shadow @Final private List<GuiMessage> allMessages;

	@Unique private static boolean polar$busy;
	@Unique private GuiMessage polar$last;

	@Shadow
	private void refreshTrimmedMessages() {
	}

	@Shadow
	public abstract void addMessage(Component contents, MessageSignature signature, GuiMessageTag tag);

	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
		at = @At("HEAD"), cancellable = true)
	private void polar$addMessage(Component contents, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
		if (polar$busy) return;
		ChatHooks.Line line = ChatHooks.incoming(contents.getString());
		if (line == null) return;
		ci.cancel();
		if (line.hide) return;
		if (line.repeat > 1) {
			if (!allMessages.isEmpty() && allMessages.get(0) == polar$last) {
				allMessages.remove(0);
				refreshTrimmedMessages();
			} else {
				ChatHooks.repeatLost();
				line.repeat = 1;
			}
		}
		polar$busy = true;
		try {
			addMessage(ChatText.decorate(contents, line), signature, tag);
		} finally {
			polar$busy = false;
		}
		polar$last = allMessages.isEmpty() ? null : allMessages.get(0);
	}

	/** The per-line chat background (the first colour made in that drawing step). */
	@Redirect(method = "lambda$render$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;color(FI)I", ordinal = 0), require = 0)
	private int polar$background(float alpha, int color) {
		int vanilla = ARGB.color(alpha, color);
		if (!ChatHooks.customBackground()) return vanilla;
		return ChatHooks.background(vanilla, Minecraft.getInstance().options.textBackgroundOpacity().get().floatValue() * 255f);
	}
}
