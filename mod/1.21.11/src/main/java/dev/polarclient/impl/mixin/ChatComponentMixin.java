package dev.polarclient.impl.mixin;

import dev.polarclient.hooks.ChatHooks;
import dev.polarclient.impl.ChatText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Consumer;

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

	@ModifyVariable(method = "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
		at = @At("HEAD"), argsOnly = true)
	private ChatComponent.ChatGraphicsAccess polar$background(ChatComponent.ChatGraphicsAccess access) {
		if (!ChatHooks.customBackground()) return access;
		final float full = Minecraft.getInstance().options.textBackgroundOpacity().get().floatValue() * 255f;
		return new ChatComponent.ChatGraphicsAccess() {
			@Override
			public void updatePose(Consumer<Matrix3x2f> updater) {
				access.updatePose(updater);
			}

			@Override
			public void fill(int x0, int y0, int x1, int y1, int color) {
				// the background is the only black fill; the scroll bar has colour
				access.fill(x0, y0, x1, y1, (color & 0xFFFFFF) == 0 ? ChatHooks.background(color, full) : color);
			}

			@Override
			public boolean handleMessage(int textTop, float opacity, FormattedCharSequence message) {
				return access.handleMessage(textTop, opacity, message);
			}

			@Override
			public void handleTag(int x0, int y0, int x1, int y1, float opacity, GuiMessageTag t) {
				access.handleTag(x0, y0, x1, y1, opacity, t);
			}

			@Override
			public void handleTagIcon(int left, int bottom, boolean forceVisible, GuiMessageTag t, GuiMessageTag.Icon icon) {
				access.handleTagIcon(left, bottom, forceVisible, t, icon);
			}
		};
	}
}
