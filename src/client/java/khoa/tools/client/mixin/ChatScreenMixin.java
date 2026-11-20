package khoa.tools.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import khoa.tools.client.chat.ChatComponentExt;
import khoa.tools.client.chat.ChatCoordinates;
import khoa.tools.client.chat.ChatLineFinder;
import khoa.tools.client.chat.ChatMessageEdit;
import khoa.tools.client.chat.RawChatText;
import khoa.tools.client.feature.betterchat.BetterChatConfig;
import khoa.tools.client.feature.chattranslator.ChatTranslatorFeature;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	@Shadow
	private ChatComponent.DisplayMode displayMode;

	@Shadow
	protected EditBox input;

	@Unique
	private GuiMessage khoatools$messageAt(MouseButtonEvent event) {
		Minecraft minecraft = Minecraft.getInstance();
		ChatComponent chat = minecraft.gui.getChat();
		ChatLineFinder finder = new ChatLineFinder((int) event.y());
		chat.captureClickableText(finder, minecraft.getWindow().getGuiScaledHeight(), minecraft.gui.getGuiTicks(), this.displayMode);
		return finder.result() == null ? null : ((ChatComponentExt) chat).khoatools$messageOfLine(finder.result());
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void khoatools$copyRawMessage(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (event.button() != 0 || !event.hasControlDown() || !event.hasShiftDown() || !BetterChatConfig.copyRaw()) {
			return;
		}

		GuiMessage message = khoatools$messageAt(event);
		if (message == null) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		minecraft.keyboardHandler.setClipboard(RawChatText.toRaw(message.content()));
		minecraft.gui.setOverlayMessage(Component.literal("Copied raw chat message to clipboard"), false);
		cir.setReturnValue(true);
	}

	// Client-side message edit: grey the message, put its raw text in the chat box (see handleChatInput below).
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void khoatools$beginEditMessage(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (event.button() != 0 || !event.hasControlDown() || !event.hasAltDown() || event.hasShiftDown()
			|| !BetterChatConfig.editMessage()) {
			return;
		}

		GuiMessage message = khoatools$messageAt(event);
		if (message == null) {
			return;
		}

		String raw = RawChatText.toRaw(message.content());
		ChatMessageEdit.begin(Minecraft.getInstance().gui.getChat(), message);
		this.input.setValue(raw);
		this.input.moveCursorToEnd(false);
		cir.setReturnValue(true);
	}

	@Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
	private void khoatools$finishEditMessage(String message, boolean addToRecent, CallbackInfo ci) {
		if (ChatMessageEdit.isEditing()) {
			ChatMessageEdit.finish(message);
			ci.cancel(); // never sent to the server
		}
	}

	@Inject(method = "removed", at = @At("HEAD"))
	private void khoatools$cancelEditMessage(CallbackInfo ci) {
		ChatMessageEdit.cancel();
	}

	@Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
	private void khoatools$copyClickedCoordinate(Style style, boolean insert, CallbackInfoReturnable<Boolean> cir) {
		if (insert && ChatTranslatorFeature.isToken(style.getInsertion())) {
			cir.setReturnValue(true); // hidden hover-translate marker: never paste it into the chat box
			return;
		}
		if (insert || !(style.getClickEvent() instanceof ClickEvent.CopyToClipboard copy)
			|| !ChatCoordinates.isCoordinate(copy.value())) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		minecraft.keyboardHandler.setClipboard(copy.value());
		minecraft.gui.setOverlayMessage(Component.literal("Copied clicked coordinate to clipboard"), false);
		cir.setReturnValue(true);
	}
}
