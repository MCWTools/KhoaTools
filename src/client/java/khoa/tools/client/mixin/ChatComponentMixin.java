package khoa.tools.client.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

import khoa.tools.client.chat.ChatComponentExt;
import khoa.tools.client.chat.ChatCoordinates;
import khoa.tools.client.feature.betterchat.BetterChatConfig;
import khoa.tools.client.feature.betterchat.ChatCache;
import khoa.tools.client.feature.chathighlight.ChatHighlightFeature;
import khoa.tools.client.feature.chattranslator.ChatTranslatorFeature;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements ChatComponentExt {
	@Shadow
	@Final
	private List<GuiMessage> allMessages;

	@Shadow
	@Final
	private List<GuiMessage.Line> trimmedMessages;

	@Shadow
	protected abstract void addMessageToDisplayQueue(GuiMessage message);

	@Shadow
	protected abstract void addMessageToQueue(GuiMessage message);

	// Vanilla keeps 100 messages / 100 wrapped lines. With a custom length we do the trimming ourselves.
	@ModifyConstant(method = "addMessageToQueue", constant = @Constant(intValue = 100))
	private int khoatools$messageLimit(int vanilla) {
		return BetterChatConfig.length() > 0 ? Integer.MAX_VALUE : vanilla;
	}

	@ModifyConstant(method = "addMessageToDisplayQueue", constant = @Constant(intValue = 100))
	private int khoatools$lineLimit(int vanilla) {
		return BetterChatConfig.length() > 0 ? Integer.MAX_VALUE : vanilla;
	}

	@Inject(method = "addMessageToQueue", at = @At("TAIL"))
	private void khoatools$trimAfterAdd(GuiMessage message, CallbackInfo ci) {
		khoatools$trimToLength();
	}

	// Remember messages that came from the server (player chat + server system messages), never client ones.
	@Inject(method = "addMessage", at = @At("HEAD"))
	private void khoatools$record(Component content, MessageSignature signature, GuiMessageSource source,
		GuiMessageTag tag, CallbackInfo ci) {
		if (source != GuiMessageSource.SYSTEM_CLIENT) {
			ChatCache.record(content);
		}
	}

	@Unique
	private GuiMessage khoatools$firstBefore;

	@Unique
	private int khoatools$sizeBefore;

	@Inject(method = "addMessage", at = @At("HEAD"))
	private void khoatools$rememberState(Component content, MessageSignature signature, GuiMessageSource source,
		GuiMessageTag tag, CallbackInfo ci) {
		this.khoatools$firstBefore = this.allMessages.isEmpty() ? null : this.allMessages.getFirst();
		this.khoatools$sizeBefore = this.allMessages.size();
	}

	// Chat Translator: the message was added as received; now hand it over so it can be replaced by the translation.
	@Inject(method = "addMessage", at = @At("TAIL"))
	private void khoatools$translate(Component content, MessageSignature signature, GuiMessageSource source,
		GuiMessageTag tag, CallbackInfo ci) {
		if (this.allMessages.isEmpty()) {
			return;
		}
		GuiMessage added = this.allMessages.getFirst();
		if (added == this.khoatools$firstBefore && this.allMessages.size() == this.khoatools$sizeBefore) {
			return; // nothing was added (message dropped)
		}
		ChatTranslatorFeature.onMessage((ChatComponent) (Object) this, added, content, signature, source, tag);
	}

	@ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Component khoatools$makeCoordinatesClickable(Component content) {
		return ChatHighlightFeature.apply(ChatCoordinates.makeClickable(content));
	}

	@Override
	public void khoatools$trimToLength() {
		int max = BetterChatConfig.length();
		if (max <= 0) {
			return;
		}
		while (this.allMessages.size() > max) {
			GuiMessage removed = this.allMessages.removeLast();
			// lines are stored newest first, so the removed message owns the tail of the list
			while (!this.trimmedMessages.isEmpty() && this.trimmedMessages.getLast().parent() == removed) {
				this.trimmedMessages.removeLast();
			}
		}
	}

	@Override
	public void khoatools$replace(GuiMessage old, GuiMessage replacement) {
		int index = -1;
		for (int i = 0; i < this.allMessages.size(); i++) {
			if (this.allMessages.get(i) == old) {
				index = i;
				break;
			}
		}
		if (index < 0) {
			return; // already trimmed away
		}
		this.allMessages.set(index, replacement);
		// same as vanilla's own rebuild: oldest first, newest lines end up first in trimmedMessages
		this.trimmedMessages.clear();
		for (int i = this.allMessages.size() - 1; i >= 0; i--) {
			this.addMessageToDisplayQueue(this.allMessages.get(i));
		}
	}

	@Override
	public void khoatools$addRestored(GuiMessage message) {
		this.addMessageToDisplayQueue(message);
		this.addMessageToQueue(message);
	}
}
