package dev.polarclient.impl.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.polarclient.hooks.ChatHooks;
import dev.polarclient.impl.ChatText;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
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
public abstract class ChatComponentMixin extends GuiComponent {
	@Shadow @Final private List<GuiMessage<Component>> allMessages;

	@Unique private static boolean polar$busy;
	@Unique private GuiMessage<Component> polar$last;

	@Shadow
	public abstract void rescaleChat();

	@Shadow
	private void addMessage(Component message, int id) {
	}

	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;I)V", at = @At("HEAD"), cancellable = true)
	private void polar$addMessage(Component message, int id, CallbackInfo ci) {
		if (polar$busy) return;
		ChatHooks.Line line = ChatHooks.incoming(message.getString());
		if (line == null) return;
		ci.cancel();
		if (line.hide) return;
		if (line.repeat > 1) {
			if (!allMessages.isEmpty() && allMessages.get(0) == polar$last) {
				allMessages.remove(0);
				rescaleChat();
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
		polar$last = allMessages.isEmpty() ? null : allMessages.get(0);
	}

	/** The per-line background (the first box chat draws for each line). */
	@Redirect(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/components/ChatComponent;fill(Lcom/mojang/blaze3d/vertex/PoseStack;IIIII)V", ordinal = 0))
	private void polar$background(PoseStack pose, int x0, int y0, int x1, int y1, int color) {
		if (ChatHooks.customBackground()) {
			color = ChatHooks.background(color, (float) Minecraft.getInstance().options.textBackgroundOpacity * 255f);
		}
		fill(pose, x0, y0, x1, y1, color);
	}
}
